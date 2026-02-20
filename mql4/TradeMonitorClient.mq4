//+------------------------------------------------------------------+
//|                                         TradeMonitorClient.mq4   |
//|                                    Trade Monitor Client EA       |
//|                        Sends trades to monitoring server         |
//+------------------------------------------------------------------+
#property copyright "TradeMonitor"
#property version   "1.00"
#property strict

//--- Input parameters (defaults, overridden by config file if present)
input string   ServerURL = "http://192.168.178.143:8080";   // Server URL (use IP, not localhost!)
input int      UpdateIntervalSeconds = 5;              // Update interval (seconds)
input int      HeartbeatIntervalSeconds = 30;          // Heartbeat interval (seconds)
input int      ReconnectIntervalSeconds = 30;          // Reconnect interval (seconds)
input int      MaxReconnectAttempts = 10;               // Max reconnect attempts (0=unlimited)

//--- Config file name (stored in MQL4/Files/)
#define CONFIG_FILE "TradeMonitorClient.cfg"
#define EA_VERSION "1.00"

//--- Active runtime parameters (loaded from config or input defaults)
string   cfg_ServerURL = "";
int      cfg_UpdateIntervalSeconds = 0;
int      cfg_HeartbeatIntervalSeconds = 0;
int      cfg_ReconnectIntervalSeconds = 0;
int      cfg_MaxReconnectAttempts = 0;

//--- Global variables
uint lastUpdateTick = 0;
uint lastHeartbeatTick = 0;
uint lastReconnectAttemptTick = 0;
bool isRegistered = false;
int httpTimeout = 5000;          // 5 seconds timeout for normal requests
int httpTimeoutInit = 180000;    // 180 seconds (3 min) timeout for initial trade list (large payload)

//--- Trade list tracking
bool g_tradeListSent = false;            // Whether full trade list was successfully sent
datetime g_tradeListSentTime = 0;        // When it was sent
uint g_lastInitRetryTime = 0;            // Cooldown tracker for init retries (ms ticks)
int g_initRetryCount = 0;                // Number of init trade list retry attempts
#define MAX_INIT_RETRIES 5               // Max retries before giving up

//--- History sync tracking
string g_lastSyncedCloseTime = "";       // Last close time successfully synced to server

//--- Reconnect tracking
int g_reconnectAttempts = 0;             // Number of reconnect attempts
bool g_extendedRetryMode = false;        // Whether we are in the 15-minute extended retry phase
datetime g_nextRetryTime = 0;            // Timestamp for next retry attempt
int g_lastError = 0;                     // Last web request error

//--- Constants
#define LONG_RETRY_INTERVAL 900          // 15 minutes (900 seconds) for long retry mode

//--- Button and Label constants
#define BTN_RECONNECT "btnReconnectServer"
#define LBL_STATUS "lblStatus"

//--- MetaTrader GlobalVariable names for persisting state
string GV_TRADELIST_SENT = "";
string GV_LAST_SYNC_TIME = "";          // Stores last synced close time as string hash

//+------------------------------------------------------------------+
//| Forward declarations                                               |
//+------------------------------------------------------------------+
bool RegisterWithServer();
bool SendInitialTradeList();
void SendTradeUpdate();
void SendHistoryUpdate();
void SendHeartbeat();
string BuildOpenTradesJson();
string BuildClosedTradesJson(string sinceCloseTime, string &outLatestCloseTime);
bool SendHttpPost(string url, string json);
bool SendHttpPostWithTimeout(string url, string json, int timeout);
string EscapeJson(string text);
void CreateReconnectButton();
void CreateStatusLabel();
void UpdateStatusLabel(string text, color col);
void LoadLastSyncTime();
void SaveLastSyncTime(string closeTime);
void DiagnosticWebRequestTest();

//+------------------------------------------------------------------+
//| Load configuration from file. Returns true if file was found.     |
//+------------------------------------------------------------------+
bool LoadConfig()
{
   if(!FileIsExist(CONFIG_FILE))
   {
      Print("Config file not found, using input defaults");
      return false;
   }
   
   int handle = FileOpen(CONFIG_FILE, FILE_READ|FILE_TXT|FILE_ANSI);
   if(handle == INVALID_HANDLE)
   {
      Print("Failed to open config file, using input defaults");
      return false;
   }
   
   while(!FileIsEnding(handle))
   {
      string line = FileReadString(handle);
      if(StringLen(line) == 0) continue;
      
      // Skip comments
      if(StringGetCharacter(line, 0) == '#') continue;
      
      // Parse key=value
      int pos = StringFind(line, "=");
      if(pos <= 0) continue;
      
      string key = StringSubstr(line, 0, pos);
      string value = StringSubstr(line, pos + 1);
      StringTrimLeft(key);
      StringTrimRight(key);
      StringTrimLeft(value);
      StringTrimRight(value);
      
      if(key == "ServerURL")                 cfg_ServerURL = value;
      else if(key == "UpdateIntervalSeconds")     cfg_UpdateIntervalSeconds = (int)StringToInteger(value);
      else if(key == "HeartbeatIntervalSeconds")  cfg_HeartbeatIntervalSeconds = (int)StringToInteger(value);
      else if(key == "ReconnectIntervalSeconds")  cfg_ReconnectIntervalSeconds = (int)StringToInteger(value);
      else if(key == "MaxReconnectAttempts")       cfg_MaxReconnectAttempts = (int)StringToInteger(value);
   }
   
   FileClose(handle);
   Print("Configuration loaded from ", CONFIG_FILE);
   return true;
}

//+------------------------------------------------------------------+
//| Save current configuration to file.                                |
//+------------------------------------------------------------------+
void SaveConfig()
{
   int handle = FileOpen(CONFIG_FILE, FILE_WRITE|FILE_TXT|FILE_ANSI);
   if(handle == INVALID_HANDLE)
   {
      Print("Failed to save config file: ", GetLastError());
      return;
   }
   
   FileWriteString(handle, "# TradeMonitorClient Configuration\r\n");
   FileWriteString(handle, "# Auto-generated - do not edit while EA is running\r\n");
   FileWriteString(handle, "ServerURL=" + cfg_ServerURL + "\r\n");
   FileWriteString(handle, "UpdateIntervalSeconds=" + IntegerToString(cfg_UpdateIntervalSeconds) + "\r\n");
   FileWriteString(handle, "HeartbeatIntervalSeconds=" + IntegerToString(cfg_HeartbeatIntervalSeconds) + "\r\n");
   FileWriteString(handle, "ReconnectIntervalSeconds=" + IntegerToString(cfg_ReconnectIntervalSeconds) + "\r\n");
   FileWriteString(handle, "MaxReconnectAttempts=" + IntegerToString(cfg_MaxReconnectAttempts) + "\r\n");
   
   FileClose(handle);
   Print("Configuration saved to ", CONFIG_FILE);
}

//+------------------------------------------------------------------+
//| Initialize config: load from file or use input defaults            |
//+------------------------------------------------------------------+
void InitConfig()
{
   // First set defaults from input parameters
   cfg_ServerURL = ServerURL;
   cfg_UpdateIntervalSeconds = UpdateIntervalSeconds;
   cfg_HeartbeatIntervalSeconds = HeartbeatIntervalSeconds;
   cfg_ReconnectIntervalSeconds = ReconnectIntervalSeconds;
   cfg_MaxReconnectAttempts = MaxReconnectAttempts;
   
   // Try to load from config file (overrides defaults)
   if(LoadConfig())
   {
      // Check if user changed input parameters (different from config file values)
      // If so, the user wants new values - save them
      // This is detected by comparing input params with what's in the config
      // If they differ, the user manually changed inputs -> use input values
      // Note: this works because MQL4/5 input params are set by the user in the dialog
   }
   else
   {
      // No config file exists, save current defaults
      SaveConfig();
   }
   
   // --- Robust URL cleanup ---
   // Remove trailing slash, carriage return, newline, and spaces
   // (can sneak in from config file reads on Windows)
   StringTrimRight(cfg_ServerURL);
   StringTrimLeft(cfg_ServerURL);
   // Remove any trailing slash
   int urlLen = StringLen(cfg_ServerURL);
   if(urlLen > 0 && StringGetCharacter(cfg_ServerURL, urlLen - 1) == '/')
      cfg_ServerURL = StringSubstr(cfg_ServerURL, 0, urlLen - 1);
   // Remove carriage return if present at end
   urlLen = StringLen(cfg_ServerURL);
   if(urlLen > 0 && StringGetCharacter(cfg_ServerURL, urlLen - 1) == 13)  // CR = 13
      cfg_ServerURL = StringSubstr(cfg_ServerURL, 0, urlLen - 1);
   
   Print("Active config: ServerURL=", cfg_ServerURL,
         " (len=", StringLen(cfg_ServerURL), ")",
         " UpdateInterval=", cfg_UpdateIntervalSeconds,
         " HeartbeatInterval=", cfg_HeartbeatIntervalSeconds,
         " ReconnectInterval=", cfg_ReconnectIntervalSeconds,
         " MaxReconnectAttempts=", cfg_MaxReconnectAttempts);
   Print(">>> MT4 Allowed URL must be EXACTLY: ", cfg_ServerURL, " (copy this!)");
}

//+------------------------------------------------------------------+
//| Expert initialization function                                     |
//+------------------------------------------------------------------+
int OnInit()
{
   Print("TradeMonitorClient EA v" + EA_VERSION + " initialized");
   
   // === DIAGNOSTIC: Test WebRequest with a public URL ===
   // This tells us if WebRequest works at ALL in this MT4 build.
   // Requires 'http://worldtimeapi.org' in Tools -> Options -> Expert Advisors -> WebRequest URLs
   DiagnosticWebRequestTest();
   
   // Load or initialize configuration
   InitConfig();
   
   // Build unique GlobalVariable names per account
   string accStr = IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN));
   GV_TRADELIST_SENT = "TM_TradeListSent_" + accStr;
   GV_LAST_SYNC_TIME = "TM_LastSync_" + accStr;
   
   // Check if trade list was already sent in a previous session
   if(GlobalVariableCheck(GV_TRADELIST_SENT))
   {
      double gvValue = GlobalVariableGet(GV_TRADELIST_SENT);
      if(gvValue > 0)
      {
         g_tradeListSent = true;
         g_tradeListSentTime = (datetime)(long)gvValue;
         Print("Trade list was already sent at ", TimeToString(g_tradeListSentTime), " (from GlobalVariable)");
      }
   }
   
   // Load last synced close time from config file
   LoadLastSyncTime();
   
   Print("Server URL: ", cfg_ServerURL);
   Print("Account ID: ", AccountInfoInteger(ACCOUNT_LOGIN));
   
   // Create reconnect button on chart
   CreateReconnectButton();
   CreateStatusLabel();
   
   // Register with server on startup
   if(RegisterWithServer())
   {
      isRegistered = true;
      g_lastError = 0;
      Print("Successfully registered with server");
      
      // Send full trade list on first connect (only if not already sent)
      if(!g_tradeListSent)
      {
         if(SendInitialTradeList())
         {
            g_tradeListSent = true;
            g_tradeListSentTime = TimeCurrent();
            GlobalVariableSet(GV_TRADELIST_SENT, (double)g_tradeListSentTime);
            Print("Initial trade list sent successfully at ", TimeToString(g_tradeListSentTime));
         }
         else
         {
            Print("Failed to send initial trade list - will be retried");
            g_lastError = GetLastError();
         }
      }
   }
   else
   {
      g_lastError = GetLastError();
      Print("Failed to register with server - will retry every ", cfg_ReconnectIntervalSeconds, " seconds");
      g_nextRetryTime = TimeCurrent() + cfg_ReconnectIntervalSeconds;
   }
   
   // Set timer for periodic updates
   EventSetTimer(1);
   
   return(INIT_SUCCEEDED);
}

//+------------------------------------------------------------------+
//| Expert deinitialization function                                   |
//+------------------------------------------------------------------+
void OnDeinit(const int reason)
{
   EventKillTimer();
   ObjectDelete(0, BTN_RECONNECT);
   ObjectDelete(0, LBL_STATUS);
   Print("TradeMonitorClient EA deinitialized");
}

//+------------------------------------------------------------------+
//| Timer function                                                     |
//+------------------------------------------------------------------+
void OnTimer()
{
   uint currentTick = GetTickCount();
   datetime now = TimeCurrent();
   
   // Check if we need to register / reconnect
   if(!isRegistered)
   {
      int secondsRemaining = (int)(g_nextRetryTime - now);
      if(secondsRemaining < 0) secondsRemaining = 0;
      
      string statusText = "";
      color statusColor = clrRed;
      
      if(g_extendedRetryMode)
      {
         statusText = "Offline (Long Retry Mode)";
         statusText += "\nNext attempt in: " + IntegerToString(secondsRemaining) + "s";
      }
      else
      {
         statusText = "Offline (Retrying...)";
         statusText += "\nNext attempt in: " + IntegerToString(secondsRemaining) + "s";
         statusText += "\nAttempt " + IntegerToString(g_reconnectAttempts + 1) + "/" + IntegerToString(cfg_MaxReconnectAttempts);
         statusColor = clrOrange;
      }
      
      if(g_lastError > 0)
      {
         statusText += "\nLast Error: " + IntegerToString(g_lastError);
      }
      
      UpdateStatusLabel(statusText, statusColor);
      
      // Wait for next retry time
      if(now < g_nextRetryTime)
         return;
      
      // Perform Retry
      g_reconnectAttempts++;
      
      if(g_extendedRetryMode)
         Print("Extended Retry attempt ", g_reconnectAttempts, " (Interval: 15 min)");
      else
         Print("Reconnect attempt ", g_reconnectAttempts, "/", cfg_MaxReconnectAttempts);
      
      if(RegisterWithServer())
      {
         // SUCCESS
         isRegistered = true;
         g_extendedRetryMode = false; // Exit extended mode
         g_reconnectAttempts = 0;
         g_lastError = 0;
         
         statusText = "Connected\nID: " + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN));
         UpdateStatusLabel(statusText, clrGreen);
         
         Print("Successfully registered with server");
         
         // Send full trade list on reconnect (if not already sent)
         if(!g_tradeListSent)
         {
            if(SendInitialTradeList())
            {
               g_tradeListSent = true;
               g_tradeListSentTime = TimeCurrent();
               GlobalVariableSet(GV_TRADELIST_SENT, (double)g_tradeListSentTime);
               Print("Initial trade list sent successfully at ", TimeToString(g_tradeListSentTime));
            }
         }
      }
      else
      {
         // FAILURE
         g_lastError = GetLastError();
         
         // Check if we should switch to extended retry mode
         if(!g_extendedRetryMode && cfg_MaxReconnectAttempts > 0 && g_reconnectAttempts >= cfg_MaxReconnectAttempts)
         {
            g_extendedRetryMode = true;
            g_reconnectAttempts = 0; // Reset counter for extended phase logic if needed
            Print("Max normal reconnect attempts reached. Switching to 15-minute extended retry mode.");
            
            // Set next retry time to LONG interval
            g_nextRetryTime = now + LONG_RETRY_INTERVAL;
         }
         else
         {
            // Determine next retry interval based on mode
            int interval = g_extendedRetryMode ? LONG_RETRY_INTERVAL : cfg_ReconnectIntervalSeconds;
            g_nextRetryTime = now + interval;
            
            Print("Reconnect failed. Next attempt in ", interval, " seconds.");
         }
      }
      return;
   }
   
   // --- CONNECTED STATE ---
   
   string statusText = "Connected\nID: " + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN));
   if(g_tradeListSent)
      statusText += "\nSync: OK";
   else
      statusText += "\nSync: Pending";
      
   UpdateStatusLabel(statusText, clrGreen);
   
   // Normal update cycle (only if registered and trade list was sent)
   if(g_tradeListSent)
   {
      // Send trade updates
      if(currentTick - lastUpdateTick >= (uint)cfg_UpdateIntervalSeconds * 1000)
      {
         SendTradeUpdate();
         SendHistoryUpdate();
         lastUpdateTick = currentTick;
      }
      
      // Send heartbeat
      if(currentTick - lastHeartbeatTick >= (uint)cfg_HeartbeatIntervalSeconds * 1000)
      {
         SendHeartbeat();
         lastHeartbeatTick = currentTick;
      }
   }
   else
   {
      // Registered but trade list not yet sent - retry with cooldown
      // Give up after MAX_INIT_RETRIES to avoid infinite loop
      if(g_initRetryCount >= MAX_INIT_RETRIES)
      {
         UpdateStatusLabel("Connected\nSync Failed (Max Retries)", clrRed);
         return;
      }
      
      // Only retry every cfg_ReconnectIntervalSeconds to avoid hammering the server
      if(currentTick - g_lastInitRetryTime >= (uint)cfg_ReconnectIntervalSeconds * 1000)
      {
         g_lastInitRetryTime = currentTick;
         g_initRetryCount++;
         Print("Retrying initial trade list send (attempt ", g_initRetryCount, "/", MAX_INIT_RETRIES, ")...");
         if(SendInitialTradeList())
         {
            g_tradeListSent = true;
            g_tradeListSentTime = TimeCurrent();
            g_initRetryCount = 0;
            GlobalVariableSet(GV_TRADELIST_SENT, (double)g_tradeListSentTime);
            Print("Initial trade list sent successfully at ", TimeToString(g_tradeListSentTime));
         }
         else
         {
             g_lastError = GetLastError();
             if(g_initRetryCount >= MAX_INIT_RETRIES)
             {
                Print("=== Max init retries (", MAX_INIT_RETRIES, ") reached. Press 'Reconnect Server' to try again. ===");
             }
             else
             {
                Print("Init trade list send failed, will retry in ", cfg_ReconnectIntervalSeconds, " seconds");
             }
         }
      }
   }
}

//+------------------------------------------------------------------+
//| Create Status label on chart                                       |
//+------------------------------------------------------------------+
void CreateStatusLabel()
{
   ObjectDelete(0, LBL_STATUS);
   
   ObjectCreate(0, LBL_STATUS, OBJ_LABEL, 0, 0, 0);
   ObjectSetInteger(0, LBL_STATUS, OBJPROP_XDISTANCE, 170); // Right of the button (10 + 150 + 10)
   ObjectSetInteger(0, LBL_STATUS, OBJPROP_YDISTANCE, 35);  // Align with button text
   ObjectSetInteger(0, LBL_STATUS, OBJPROP_CORNER, CORNER_LEFT_UPPER);
   ObjectSetString(0, LBL_STATUS, OBJPROP_TEXT, "MQL4 Client v" + EA_VERSION + ": Initializing...");
   ObjectSetInteger(0, LBL_STATUS, OBJPROP_COLOR, clrWhite);
   ObjectSetInteger(0, LBL_STATUS, OBJPROP_FONTSIZE, 10);
   ObjectSetInteger(0, LBL_STATUS, OBJPROP_SELECTABLE, false);
   
   ChartRedraw(0);
}

//+------------------------------------------------------------------+
//| Update Status label text and color                                 |
//+------------------------------------------------------------------+
void UpdateStatusLabel(string text, color col)
{
   ObjectSetString(0, LBL_STATUS, OBJPROP_TEXT, "Ver: " + EA_VERSION + "\n" + text);
   ObjectSetInteger(0, LBL_STATUS, OBJPROP_COLOR, col);
   ChartRedraw(0);
}

//+------------------------------------------------------------------+
//| Chart event handler (for button clicks)                            |
//+------------------------------------------------------------------+
void OnChartEvent(const int id,
                  const long &lparam,
                  const double &dparam,
                  const string &sparam)
{
   if(id == CHARTEVENT_OBJECT_CLICK)
   {
      if(sparam == BTN_RECONNECT)
      {
         // Reset button state (unpress)
         ObjectSetInteger(0, BTN_RECONNECT, OBJPROP_STATE, false);
         
         Print("=== Manual Reconnect triggered by user ===");
         
         // Reset all reconnect state
         g_reconnectAttempts = 0;
         g_extendedRetryMode = false;
         g_nextRetryTime = 0;
         UpdateStatusLabel("MQL4 Client: Connection setup", clrOrange);
         g_tradeListSent = false;
         g_tradeListSentTime = 0;
         g_lastInitRetryTime = 0;
         g_initRetryCount = 0;
         g_lastSyncedCloseTime = "";
         isRegistered = false;
         lastReconnectAttemptTick = 0;
         GlobalVariableSet(GV_TRADELIST_SENT, 0);  // Clear persisted status
         SaveLastSyncTime("");                     // Clear sync bookmark
         
         // Try immediate reconnect
         if(RegisterWithServer())
         {
            isRegistered = true;
            g_extendedRetryMode = false;
            UpdateStatusLabel("MQL4 Client: Connected", clrGreen);
            Print("Manual reconnect: Successfully registered with server");
            
            if(SendInitialTradeList())
            {
               g_tradeListSent = true;
               g_tradeListSentTime = TimeCurrent();
               GlobalVariableSet(GV_TRADELIST_SENT, (double)g_tradeListSentTime);
               Print("Manual reconnect: Full trade list sent at ", TimeToString(g_tradeListSentTime));
            }
            else
            {
               Print("Manual reconnect: Failed to send trade list - will retry");
            }
         }
         else
         {
            UpdateStatusLabel("MQL4 Client: Connection setup", clrOrange);
            Print("Manual reconnect: Failed to register - will retry automatically");
         }
      }
   }
}

//+------------------------------------------------------------------+
//| Create Reconnect button on chart                                   |
//+------------------------------------------------------------------+
void CreateReconnectButton()
{
   ObjectDelete(0, BTN_RECONNECT);
   
   ObjectCreate(0, BTN_RECONNECT, OBJ_BUTTON, 0, 0, 0);
   ObjectSetInteger(0, BTN_RECONNECT, OBJPROP_XDISTANCE, 10);
   ObjectSetInteger(0, BTN_RECONNECT, OBJPROP_YDISTANCE, 30);
   ObjectSetInteger(0, BTN_RECONNECT, OBJPROP_XSIZE, 150);
   ObjectSetInteger(0, BTN_RECONNECT, OBJPROP_YSIZE, 30);
   ObjectSetInteger(0, BTN_RECONNECT, OBJPROP_CORNER, CORNER_LEFT_UPPER);
   ObjectSetString(0, BTN_RECONNECT, OBJPROP_TEXT, "Reconnect Server");
   ObjectSetInteger(0, BTN_RECONNECT, OBJPROP_COLOR, clrWhite);
   ObjectSetInteger(0, BTN_RECONNECT, OBJPROP_BGCOLOR, clrDarkBlue);
   ObjectSetInteger(0, BTN_RECONNECT, OBJPROP_BORDER_COLOR, clrSilver);
   ObjectSetInteger(0, BTN_RECONNECT, OBJPROP_FONTSIZE, 10);
   ObjectSetInteger(0, BTN_RECONNECT, OBJPROP_STATE, false);
   ObjectSetInteger(0, BTN_RECONNECT, OBJPROP_SELECTABLE, false);
   
   ChartRedraw(0);
}

//+------------------------------------------------------------------+
//| Register with monitoring server                                    |
//+------------------------------------------------------------------+
bool RegisterWithServer()
{
   string url = cfg_ServerURL + "/api/register";
   
   // Build JSON payload
   string json = "{";
   json += "\"accountId\":" + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)) + ",";
   json += "\"broker\":\"" + AccountInfoString(ACCOUNT_COMPANY) + "\",";
   json += "\"currency\":\"" + AccountInfoString(ACCOUNT_CURRENCY) + "\",";
   json += "\"balance\":" + DoubleToString(AccountInfoDouble(ACCOUNT_BALANCE), 2) + ",";
   json += "\"timestamp\":\"" + TimeToString(TimeCurrent(), TIME_DATE|TIME_SECONDS) + "\"";
   json += "}";
   
   return SendHttpPost(url, json);
}

//+------------------------------------------------------------------+
//| Send initial full trade list (open + history) to server            |
//+------------------------------------------------------------------+
bool SendInitialTradeList()
{
   string url = cfg_ServerURL + "/api/trades-init";
   
   Print("Sending initial trade list to server...");
   
   // Build open trades array
   Print("Building open trades JSON...");
   string tradesJson = BuildOpenTradesJson();
   
   // Build closed trades array (full history)
   Print("Building closed trades JSON (full history)...");
   string latestCloseTime = "";
   string historyJson = BuildClosedTradesJson("", latestCloseTime);
   
   // Build main JSON payload
   string json = "{";
   json += "\"accountId\":" + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)) + ",";
   json += "\"trades\":" + tradesJson + ",";
   json += "\"closedTrades\":" + historyJson + ",";
   json += "\"equity\":" + DoubleToString(AccountInfoDouble(ACCOUNT_EQUITY), 2) + ",";
   json += "\"balance\":" + DoubleToString(AccountInfoDouble(ACCOUNT_BALANCE), 2) + ",";
   json += "\"timestamp\":\"" + TimeToString(TimeCurrent(), TIME_DATE|TIME_SECONDS) + "\"";
   json += "}";
   
   int payloadSizeKB = StringLen(json) / 1024;
   Print("Sending payload (", payloadSizeKB, " KB) to server (timeout: ", httpTimeoutInit/1000, "s)...");
   
   // Use extended timeout for initial large payload
   if(SendHttpPostWithTimeout(url, json, httpTimeoutInit))
   {
      // Remember what we synced so incremental updates only send new trades
      g_lastSyncedCloseTime = latestCloseTime;
      SaveLastSyncTime(latestCloseTime);
      Print("Last synced close time: ", latestCloseTime);
      return true;
   }
   return false;
}

//+------------------------------------------------------------------+
//| Build JSON array of open trades                                    |
//+------------------------------------------------------------------+
string BuildOpenTradesJson()
{
   string tradesJson = "[";
   int totalOrders = OrdersTotal();
   bool firstTrade = true;
   
   for(int i = 0; i < totalOrders; i++)
   {
      if(OrderSelect(i, SELECT_BY_POS, MODE_TRADES))
      {
         // Filter for BUY/SELL only (Pending orders are excluded if you want only active positions)
         int type = OrderType();
         if(type == OP_BUY || type == OP_SELL)
         {
             if(!firstTrade) tradesJson += ",";
             firstTrade = false;
             
             long ticket = OrderTicket();
             string symbol = OrderSymbol();
             string typeStr = (type == OP_BUY) ? "BUY" : "SELL";
             
             tradesJson += "{";
             tradesJson += "\"ticket\":" + IntegerToString(ticket) + ",";
             tradesJson += "\"symbol\":\"" + symbol + "\",";
             tradesJson += "\"type\":\"" + typeStr + "\",";
             tradesJson += "\"volume\":" + DoubleToString(OrderLots(), 2) + ",";
             tradesJson += "\"openPrice\":" + DoubleToString(OrderOpenPrice(), 5) + ",";
             tradesJson += "\"openTime\":\"" + TimeToString(OrderOpenTime(), TIME_DATE|TIME_SECONDS) + "\",";
             tradesJson += "\"stopLoss\":" + DoubleToString(OrderStopLoss(), 5) + ",";
             tradesJson += "\"takeProfit\":" + DoubleToString(OrderTakeProfit(), 5) + ",";
             tradesJson += "\"profit\":" + DoubleToString(OrderProfit(), 2) + ",";
             tradesJson += "\"swap\":" + DoubleToString(OrderSwap(), 2) + ",";
             tradesJson += "\"magicNumber\":" + IntegerToString(OrderMagicNumber()) + ",";
             tradesJson += "\"comment\":\"" + EscapeJson(OrderComment()) + "\"";
             tradesJson += "}";
         }
      }
   }
   tradesJson += "]";
   return tradesJson;
}

//+------------------------------------------------------------------+
//| Build JSON array of closed trades (history)                        |
//+------------------------------------------------------------------+
string BuildClosedTradesJson(string sinceCloseTime, string &outLatestCloseTime)
{
   string historyJson = "[";
   int totalOrders = OrdersHistoryTotal();
   int closedCount = 0;
   int skippedCount = 0;
   bool firstDeal = true;
   outLatestCloseTime = "";
   
   bool isIncremental = (sinceCloseTime != "" && StringLen(sinceCloseTime) > 0);
   if(!isIncremental)
      Print("Processing full history: ", totalOrders, " orders found...");
   
   for(int i = 0; i < totalOrders; i++)
   {
      if(OrderSelect(i, SELECT_BY_POS, MODE_HISTORY))
      {
         int type = OrderType();
         // Only BUY/SELL orders (no BALANCE/CREDIT operations if irrelevant)
         // Usually we want actual trades.
         if (type == OP_BUY || type == OP_SELL)
         {
             datetime closeTimeVal = OrderCloseTime();
             
             // In MQL4 history, OrderCloseTime() > 0 usually indicates closed
             if(closeTimeVal > 0)
             {
                 string closeTime = TimeToString(closeTimeVal, TIME_DATE|TIME_SECONDS);
                 
                 // Skip trades already synced (incremental mode)
                 if(isIncremental && closeTime <= sinceCloseTime)
                 {
                    skippedCount++;
                    continue;
                 }
                 
                 if(!firstDeal) historyJson += ",";
                 firstDeal = false;
                 closedCount++;
                 
                 // Track the latest close time
                 if(closeTime > outLatestCloseTime)
                    outLatestCloseTime = closeTime;
                 
                 string typeStr = (type == OP_BUY) ? "BUY" : "SELL";
                 
                 historyJson += "{";
                 historyJson += "\"ticket\":" + IntegerToString(OrderTicket()) + ",";
                 historyJson += "\"symbol\":\"" + OrderSymbol() + "\",";
                 historyJson += "\"type\":\"" + typeStr + "\",";
                 historyJson += "\"volume\":" + DoubleToString(OrderLots(), 2) + ",";
                 historyJson += "\"openPrice\":" + DoubleToString(OrderOpenPrice(), 5) + ",";
                 historyJson += "\"closePrice\":" + DoubleToString(OrderClosePrice(), 5) + ",";
                 historyJson += "\"openTime\":\"" + TimeToString(OrderOpenTime(), TIME_DATE|TIME_SECONDS) + "\",";
                 historyJson += "\"closeTime\":\"" + closeTime + "\",";
                 historyJson += "\"profit\":" + DoubleToString(OrderProfit(), 2) + ",";
                 historyJson += "\"swap\":" + DoubleToString(OrderSwap(), 2) + ",";
                 historyJson += "\"commission\":" + DoubleToString(OrderCommission(), 2) + ","; // Warning: OrderCommission() might not be reliable on all brokers
                 historyJson += "\"magicNumber\":" + IntegerToString(OrderMagicNumber()) + ",";
                 historyJson += "\"comment\":\"" + EscapeJson(OrderComment()) + "\"";
                 historyJson += "}";
                 
                 // Progress logging every 100 closed trades
                 if(!isIncremental && closedCount % 100 == 0)
                 {
                    int pct = (int)((double)i / (double)totalOrders * 100.0);
                    Print("History progress: ", closedCount, " closed trades processed (", pct, "% scanned)");
                 }
             }
         }
      }
   }
   historyJson += "]";
   if(isIncremental)
   {
      if(closedCount > 0)
         Print("Incremental sync: ", closedCount, " new trades (skipped ", skippedCount, " already synced)");
   }
   else
      Print("History complete: ", closedCount, " closed trades from ", totalOrders, " total history orders");
   return historyJson;
}

//+------------------------------------------------------------------+
//| Send trade update to server (incremental, after init)              |
//+------------------------------------------------------------------+
void SendTradeUpdate()
{
   string url = cfg_ServerURL + "/api/trades";
   
   string tradesJson = BuildOpenTradesJson();
   
   // Build main JSON payload
   string json = "{";
   json += "\"accountId\":" + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)) + ",";
   json += "\"trades\":" + tradesJson + ",";
   json += "\"equity\":" + DoubleToString(AccountInfoDouble(ACCOUNT_EQUITY), 2) + ",";
   json += "\"balance\":" + DoubleToString(AccountInfoDouble(ACCOUNT_BALANCE), 2) + ",";
   json += "\"timestamp\":\"" + TimeToString(TimeCurrent(), TIME_DATE|TIME_SECONDS) + "\"";
   json += "}";
   
   if(!SendHttpPost(url, json))
   {
      isRegistered = false;  // Will trigger re-registration
   }
}

//+------------------------------------------------------------------+
//| Send history update to server (incremental)                        |
//+------------------------------------------------------------------+
void SendHistoryUpdate()
{
   string url = cfg_ServerURL + "/api/history";
   
   // Only send trades newer than last synced close time (incremental)
   string latestCloseTime = "";
   string historyJson = BuildClosedTradesJson(g_lastSyncedCloseTime, latestCloseTime);
   
   // Skip if no new trades
   if(historyJson == "[]")
      return;
   
   // Build main JSON payload
   string json = "{";
   json += "\"accountId\":" + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)) + ",";
   json += "\"closedTrades\":" + historyJson + ",";
   json += "\"timestamp\":\"" + TimeToString(TimeCurrent(), TIME_DATE|TIME_SECONDS) + "\"";
   json += "}";
   
   if(SendHttpPost(url, json))
   {
      // Update sync bookmark on success
      if(latestCloseTime != "")
      {
         g_lastSyncedCloseTime = latestCloseTime;
         SaveLastSyncTime(latestCloseTime);
         Print("History sync updated, latest close time: ", latestCloseTime);
      }
   }
}

//+------------------------------------------------------------------+
//| Send heartbeat to server                                           |
//+------------------------------------------------------------------+
void SendHeartbeat()
{
   string url = cfg_ServerURL + "/api/heartbeat";
   
   string json = "{";
   json += "\"accountId\":" + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)) + ",";
   json += "\"timestamp\":\"" + TimeToString(TimeCurrent(), TIME_DATE|TIME_SECONDS) + "\"";
   json += "}";
   
   if(!SendHttpPost(url, json))
   {
      isRegistered = false;  // Will trigger re-registration
   }
}

//+------------------------------------------------------------------+
//| Send HTTP POST request                                             |
//+------------------------------------------------------------------+
bool SendHttpPost(string url, string json)
{
   return SendHttpPostWithTimeout(url, json, httpTimeout);
}

//+------------------------------------------------------------------+
//| Send HTTP POST request with configurable timeout                   |
//+------------------------------------------------------------------+
bool SendHttpPostWithTimeout(string url, string json, int timeout)
{
   char postData[];
   char result[];
   string resultHeaders;
   
   // Convert string to char array
   StringToCharArray(json, postData, 0, WHOLE_ARRAY, CP_UTF8);
   int size = ArraySize(postData);
   if (size > 0 && postData[size-1] == 0) 
       ArrayResize(postData, size - 1);  // Remove null terminator
   
   string headers = "Content-Type: application/json\r\n";
   
   ResetLastError();
   
   // Debug: print exact URL before sending
   Print("[WebRequest] POST to: ", url, " (urlLen=", StringLen(url), " dataLen=", ArraySize(postData), " timeout=", timeout, "ms)");
   
   // MQL4 WebRequest signature is same as MQL5
   int res = WebRequest("POST", url, headers, timeout, postData, result, resultHeaders);
   
   if(res == -1)
   {
      int error = GetLastError();
      if(error == 4060)
      {
         Print("Error 4060: WebRequest not allowed. Enable 'WebRequest for listed URLs' in Tools -> Options -> Expert Advisors and add: ", cfg_ServerURL);
      }
      else if(error == 5200)
      {
         Print("Error 5200 (INVALID_ADDRESS): URL rejected by MT4. URL was: '", url, "'");
         Print("  Fix: In MT4 -> Tools -> Options -> Expert Advisors -> add EXACTLY: '", cfg_ServerURL, "'");
         Print("  (check for typos, no trailing slash, must match http:// prefix with port)");
      }
      else if(error == 5201)
         Print("Error 5201 (CONNECT_FAILED): Cannot reach server at ", url, " - check if server is running");
      else if(error == 5202)
         Print("Error 5202 (TIMEOUT): Request timed out after ", timeout, "ms - server too slow or unreachable");
      else
      {
         Print("HTTP request failed, error: ", error, " (timeout was ", timeout, "ms) URL: ", url);
      }
      return false;
   }
   
   if(res != 200)
   {
      // Read server error response body for debugging
      string responseBody = CharArrayToString(result, 0, WHOLE_ARRAY, CP_UTF8);
      Print("HTTP request returned status: ", res, " URL: ", url);
      if(StringLen(responseBody) > 0)
      {
         // Truncate very long responses
         if(StringLen(responseBody) > 500)
            responseBody = StringSubstr(responseBody, 0, 500) + "...";
         Print("Server response: ", responseBody);
      }
      return false;
   }
   
   return true;
}

//+------------------------------------------------------------------+
//| Save last synced close time to config file                         |
//+------------------------------------------------------------------+
void SaveLastSyncTime(string closeTime)
{
   string filename = "TM_LastSync_" + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)) + ".dat";
   int handle = FileOpen(filename, FILE_WRITE|FILE_TXT|FILE_ANSI);
   if(handle != INVALID_HANDLE)
   {
      FileWriteString(handle, closeTime);
      FileClose(handle);
   }
}

//+------------------------------------------------------------------+
//| Load last synced close time from config file                       |
//+------------------------------------------------------------------+
void LoadLastSyncTime()
{
   string filename = "TM_LastSync_" + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)) + ".dat";
   if(!FileIsExist(filename))
      return;
   
   int handle = FileOpen(filename, FILE_READ|FILE_TXT|FILE_ANSI);
   if(handle != INVALID_HANDLE)
   {
      g_lastSyncedCloseTime = FileReadString(handle);
      FileClose(handle);
      if(StringLen(g_lastSyncedCloseTime) > 0)
         Print("Loaded last sync time: ", g_lastSyncedCloseTime);
   }
}

//+------------------------------------------------------------------+
//| Escape special characters for JSON string                          |
//+------------------------------------------------------------------+
string EscapeJson(string text)
{
   string result = text;
   StringReplace(result, "\\", "\\\\");
   StringReplace(result, "\"", "\\\"");
   StringReplace(result, "\n", "\\n");
   StringReplace(result, "\r", "\\r");
   StringReplace(result, "\t", "\\t");
   return result;
}

//+------------------------------------------------------------------+
//| Diagnostic: Extended WebRequest URL matching test                 |
//+------------------------------------------------------------------+
void DiagnosticTestUrl(string label, string url)
{
   char getData[];
   char result[];
   string resultHeaders;
   string headers = "Content-Type: application/json\r\n";
   char postData[];
   string json = "{\"accountId\":0,\"test\":true}";
   StringToCharArray(json, postData, 0, WHOLE_ARRAY, CP_UTF8);
   int sz = ArraySize(postData);
   if(sz > 0 && postData[sz-1] == 0) ArrayResize(postData, sz - 1);
   
   ResetLastError();
   int res = WebRequest("POST", url, headers, 3000, postData, result, resultHeaders);
   int err = GetLastError();
   
   string status = "";
   if(res == -1)
   {
      if(err == 4060) status = "ERROR 4060 (WebRequest disabled in build!)";
      else if(err == 5200) status = "ERROR 5200 (WHITELIST MISS - URL not in allowed list)";
      else if(err == 5201) status = "ERROR 5201 (Connect failed - server not reachable)";
      else if(err == 5202) status = "ERROR 5202 (Timeout)";
      else if(err == 5203) status = "ERROR 5203 (Request failed - but whitelist OK!)";
      else status = "ERROR " + IntegerToString(err);
   }
   else
      status = "HTTP " + IntegerToString(res);
      
   Print("[DIAG] ", label, " -> ", status, " | url='", url, "' len=", StringLen(url));
}

void DiagnosticWebRequestTest()
{
   Print("=== DIAGNOSTIC WebRequest Test ===");
   string ip = "192.168.178.143";
   
   // --- URL info ---
   Print("[DIAG] cfg_ServerURL = '", cfg_ServerURL, "' len=", StringLen(cfg_ServerURL));
   for(int i = StringLen(cfg_ServerURL)-3; i < StringLen(cfg_ServerURL); i++)
      if(i >= 0) Print("[DIAG] char[", i, "] = ", StringGetCharacter(cfg_ServerURL, i));
   
   // --- Test 1: Public URL (worldtimeapi.org) ---
   // Add 'http://worldtimeapi.org' to whitelist to test internet access
   DiagnosticTestUrl("PUBLIC  worldtimeapi.org", "http://worldtimeapi.org/api/timezone/Europe/Berlin");
   
   // --- Test 2: Local IP + Port (what the EA currently uses) ---
   DiagnosticTestUrl("LOCAL   with :8080", "http://" + ip + ":8080/api/register");
   
   // --- Test 3: Local IP without Port (what portproxy should handle) ---
   DiagnosticTestUrl("LOCAL   no port  ", "http://" + ip + "/api/register");
   
   // --- Test 4: localhost without port ---
   DiagnosticTestUrl("LOCAL   localhost ", "http://localhost/api/register");
   
   // --- Test 5: 127.0.0.1 without port ---
   DiagnosticTestUrl("LOCAL   127.0.0.1", "http://127.0.0.1/api/register");
   
   Print("=== DIAGNOSTIC End ===");
   Print("[DIAG] Whitelist hint: 5200=not whitelisted, 5201=whitelisted+no reach, 5203=whitelisted+reached");
}

//+------------------------------------------------------------------+
//| Tick function - not used but required                              |
//+------------------------------------------------------------------+
void OnTick()
{
   // We use timer instead of tick for consistency
}
//+------------------------------------------------------------------+
