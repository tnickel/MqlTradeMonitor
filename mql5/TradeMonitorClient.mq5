//+------------------------------------------------------------------+
//|                                         TradeMonitorClient.mq5   |
//|                                    Trade Monitor Client EA       |
//|                        Sends trades to monitoring server         |
//+------------------------------------------------------------------+
#property copyright "TradeMonitor"
#property version   "2.10"
#property strict

//--- Input parameters (defaults, overridden by config file if present)
input string   ServerURL = "http://127.0.0.1:8080";   // Server URL (use IP, not localhost!)
input int      UpdateIntervalSeconds = 5;              // Update interval (seconds)
input int      HeartbeatIntervalSeconds = 30;          // Heartbeat interval (seconds)
input int      ReconnectIntervalSeconds = 30;          // Reconnect interval (seconds)
input int      MaxReconnectAttempts = 10;               // Max reconnect attempts (0=unlimited)

//--- Config file name (stored in MQL5/Files/)
#define CONFIG_FILE "TradeMonitorClient.cfg"

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
bool g_maxAttemptsReached = false;       // Whether max attempts reached (stop trying)

//--- Button constants
#define BTN_RECONNECT "btnReconnectServer"

//--- MetaTrader GlobalVariable names for persisting state
string GV_TRADELIST_SENT = "";
string GV_LAST_SYNC_TIME = "";          // Stores last synced close time as string hash

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
      // Note: this works because MQL5 input params are set by the user in the dialog
   }
   else
   {
      // No config file exists, save current defaults
      SaveConfig();
   }
   
   Print("Active config: ServerURL=", cfg_ServerURL,
         " UpdateInterval=", cfg_UpdateIntervalSeconds,
         " HeartbeatInterval=", cfg_HeartbeatIntervalSeconds,
         " ReconnectInterval=", cfg_ReconnectIntervalSeconds,
         " MaxReconnectAttempts=", cfg_MaxReconnectAttempts);
}

//+------------------------------------------------------------------+
//| Expert initialization function                                     |
//+------------------------------------------------------------------+
int OnInit()
{
   Print("TradeMonitorClient EA v2.10 initialized");
   
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
   
   // Register with server on startup
   if(RegisterWithServer())
   {
      isRegistered = true;
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
         }
      }
   }
   else
   {
      Print("Failed to register with server - will retry every ", cfg_ReconnectIntervalSeconds, " seconds");
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
   Print("TradeMonitorClient EA deinitialized");
}

//+------------------------------------------------------------------+
//| Timer function                                                     |
//+------------------------------------------------------------------+
void OnTimer()
{
   uint currentTick = GetTickCount();
   
   // Check if we need to register / reconnect
   if(!isRegistered)
   {
      // Stop trying if max attempts reached
      if(g_maxAttemptsReached)
         return;
      
      // Only try every cfg_ReconnectIntervalSeconds
      if(currentTick - lastReconnectAttemptTick < (uint)cfg_ReconnectIntervalSeconds * 1000)
         return;
      
      lastReconnectAttemptTick = currentTick;
      g_reconnectAttempts++;
      
      Print("Reconnect attempt ", g_reconnectAttempts, "/", cfg_MaxReconnectAttempts);
      
      if(RegisterWithServer())
      {
         isRegistered = true;
         Print("Successfully registered with server on attempt ", g_reconnectAttempts);
         g_reconnectAttempts = 0;
         
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
         // Check if max attempts reached
         if(MaxReconnectAttempts > 0 && g_reconnectAttempts >= MaxReconnectAttempts)
         {
            g_maxAttemptsReached = true;
            Print("Max reconnect attempts (", MaxReconnectAttempts, ") reached. No more automatic retries.");
            Print("Press 'Reconnect Server' button to try again.");
         }
      }
      return;
   }
   
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
         // Already logged the max-retries message, just wait for manual reconnect
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
         g_maxAttemptsReached = false;
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
   int totalPositions = PositionsTotal();
   bool firstTrade = true;
   
   for(int i = 0; i < totalPositions; i++)
   {
      ulong ticket = PositionGetTicket(i);
      if(ticket > 0)
      {
         if(!firstTrade) tradesJson += ",";
         firstTrade = false;
         
         tradesJson += "{";
         tradesJson += "\"ticket\":" + IntegerToString(ticket) + ",";
         tradesJson += "\"symbol\":\"" + PositionGetString(POSITION_SYMBOL) + "\",";
         tradesJson += "\"type\":\"" + (PositionGetInteger(POSITION_TYPE) == POSITION_TYPE_BUY ? "BUY" : "SELL") + "\",";
         tradesJson += "\"volume\":" + DoubleToString(PositionGetDouble(POSITION_VOLUME), 2) + ",";
         tradesJson += "\"openPrice\":" + DoubleToString(PositionGetDouble(POSITION_PRICE_OPEN), 5) + ",";
         tradesJson += "\"openTime\":\"" + TimeToString((datetime)PositionGetInteger(POSITION_TIME), TIME_DATE|TIME_SECONDS) + "\",";
         tradesJson += "\"stopLoss\":" + DoubleToString(PositionGetDouble(POSITION_SL), 5) + ",";
         tradesJson += "\"takeProfit\":" + DoubleToString(PositionGetDouble(POSITION_TP), 5) + ",";
         tradesJson += "\"profit\":" + DoubleToString(PositionGetDouble(POSITION_PROFIT), 2) + ",";
         tradesJson += "\"swap\":" + DoubleToString(PositionGetDouble(POSITION_SWAP), 2) + ",";
         tradesJson += "\"magicNumber\":" + IntegerToString(PositionGetInteger(POSITION_MAGIC)) + ",";
         tradesJson += "\"comment\":\"" + EscapeJson(PositionGetString(POSITION_COMMENT)) + "\"";
         tradesJson += "}";
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
   // Select ALL available history (since year 2000)
   datetime fromDate = D'2000.01.01 00:00:00';
   datetime toDate = TimeCurrent();
   
   if(!HistorySelect(fromDate, toDate))
   {
      Print("Failed to select history");
      return "[]";
   }
   
   string historyJson = "[";
   int totalDeals = HistoryDealsTotal();
   int closedCount = 0;
   int skippedCount = 0;
   bool firstDeal = true;
   outLatestCloseTime = "";
   
   bool isIncremental = (sinceCloseTime != "" && StringLen(sinceCloseTime) > 0);
   if(!isIncremental)
      Print("Processing full history: ", totalDeals, " deals found...");
   
   for(int i = 0; i < totalDeals; i++)
   {
      ulong ticket = HistoryDealGetTicket(i);
      if(ticket > 0)
      {
         ENUM_DEAL_ENTRY entry = (ENUM_DEAL_ENTRY)HistoryDealGetInteger(ticket, DEAL_ENTRY);
         if(entry == DEAL_ENTRY_OUT)
         {
            string closeTime = TimeToString((datetime)HistoryDealGetInteger(ticket, DEAL_TIME), TIME_DATE|TIME_SECONDS);
            
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
            
            ENUM_DEAL_TYPE dealType = (ENUM_DEAL_TYPE)HistoryDealGetInteger(ticket, DEAL_TYPE);
            string typeStr = (dealType == DEAL_TYPE_BUY) ? "BUY" : "SELL";
            
            historyJson += "{";
            historyJson += "\"ticket\":" + IntegerToString(ticket) + ",";
            historyJson += "\"symbol\":\"" + HistoryDealGetString(ticket, DEAL_SYMBOL) + "\",";
            historyJson += "\"type\":\"" + typeStr + "\",";
            historyJson += "\"volume\":" + DoubleToString(HistoryDealGetDouble(ticket, DEAL_VOLUME), 2) + ",";
            historyJson += "\"openPrice\":" + DoubleToString(HistoryDealGetDouble(ticket, DEAL_PRICE), 5) + ",";
            historyJson += "\"closePrice\":" + DoubleToString(HistoryDealGetDouble(ticket, DEAL_PRICE), 5) + ",";
            historyJson += "\"openTime\":\"" + TimeToString((datetime)HistoryDealGetInteger(ticket, DEAL_TIME), TIME_DATE|TIME_SECONDS) + "\",";
            historyJson += "\"closeTime\":\"" + closeTime + "\",";
            historyJson += "\"profit\":" + DoubleToString(HistoryDealGetDouble(ticket, DEAL_PROFIT), 2) + ",";
            historyJson += "\"swap\":" + DoubleToString(HistoryDealGetDouble(ticket, DEAL_SWAP), 2) + ",";
            historyJson += "\"commission\":" + DoubleToString(HistoryDealGetDouble(ticket, DEAL_COMMISSION), 2) + ",";
            historyJson += "\"magicNumber\":" + IntegerToString(HistoryDealGetInteger(ticket, DEAL_MAGIC)) + ",";
            historyJson += "\"comment\":\"" + EscapeJson(HistoryDealGetString(ticket, DEAL_COMMENT)) + "\"";
            historyJson += "}";
            
            // Progress logging every 100 closed trades
            if(!isIncremental && closedCount % 100 == 0)
            {
               int pct = (int)((double)i / (double)totalDeals * 100.0);
               Print("History progress: ", closedCount, " closed trades processed (", pct, "% of deals scanned)");
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
      Print("History complete: ", closedCount, " closed trades from ", totalDeals, " total deals");
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
   ArrayResize(postData, ArraySize(postData) - 1);  // Remove null terminator
   
   string headers = "Content-Type: application/json\r\n";
   
   ResetLastError();
   int res = WebRequest("POST", url, headers, timeout, postData, result, resultHeaders);
   
   if(res == -1)
   {
      int error = GetLastError();
      if(error == 4060)
      {
         Print("Error: URL not allowed. Please add ", cfg_ServerURL, " to allowed URLs in Tools -> Options -> Expert Advisors");
      }
      else
      {
         Print("HTTP request failed, error: ", error, " (timeout was ", timeout, "ms)");
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
//| Tick function - not used but required                              |
//+------------------------------------------------------------------+
void OnTick()
{
   // We use timer instead of tick for consistency
}
//+------------------------------------------------------------------+
