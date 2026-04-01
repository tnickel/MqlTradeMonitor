//+------------------------------------------------------------------+
//|                                         TradeMonitorClient.mq4   |
//|                                    Trade Monitor Client EA       |
//|                        Sends trades to monitoring server         |
//+------------------------------------------------------------------+
#property copyright "TradeMonitor"
#property version   "1.03"
#property strict

//--- Input parameters (defaults, overridden by config file if present)
input string   ServerURL = "https://DEINE-DOMAIN.DE:8080";   // Server URL (use HTTPS!)
input string   InpUserKey = "";                        // User API-Key (from Admin Dashboard)
input int      UpdateIntervalSeconds = 5;              // Update interval (seconds)
input int      HeartbeatIntervalSeconds = 30;          // Heartbeat interval (seconds)
input int      ReconnectIntervalSeconds = 30;          // Reconnect interval (seconds)
input int      MaxReconnectAttempts = 10;               // Max reconnect attempts (0=unlimited)
input int      LogUploadIntervalSeconds = 300;         // EA Log upload interval (seconds, 0=disabled)
input int      DebugMode = 0;                          // Debug Logging (0=Off, 1=On)

//--- DLL imports
#import "kernel32.dll"
bool CopyFileW(string lpExistingFileName, string lpNewFileName, bool bFailIfExists);
#import

//--- Config file name (stored in MQL4/Files/)
#define CONFIG_FILE "TradeMonitorClient.cfg"
#define EA_VERSION "1.04"

//--- Active runtime parameters (loaded from config or input defaults)
string   cfg_ServerURL = "";
string   cfg_UserKey = "";
int      cfg_UpdateIntervalSeconds = 0;
int      cfg_HeartbeatIntervalSeconds = 0;
int      cfg_ReconnectIntervalSeconds = 0;
int      cfg_MaxReconnectAttempts = 0;
int      cfg_LogUploadIntervalSeconds = 0;
int      cfg_DebugMode = 0;

//--- Global variables
uint lastUpdateTick = 0;
uint lastHeartbeatTick = 0;
uint lastReconnectAttemptTick = 0;
uint lastLogUploadTick = 0;
bool isRegistered = false;
int httpTimeout = 5000;          // 5 seconds timeout for normal requests
int httpTimeoutInit = 180000;    // 180 seconds (3 min) timeout for initial trade list (large payload)

//--- EA Log tracking
int g_lastLogLinesSent = 0;              // Number of log lines already sent to server
string GV_TRADELIST_SENT = "";           // GlobalVariable name for persisting trade list sync status
string GV_LAST_LOG_LINES = "";           // GlobalVariable name for persisting log position
string GV_LAST_LOG_DATE = "";            // GlobalVariable name for persisting the log date format YYYYMMDD

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
void SendEaLogs();

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
      else if(key == "UserKey")              cfg_UserKey = value;
      else if(key == "UpdateIntervalSeconds")     cfg_UpdateIntervalSeconds = (int)StringToInteger(value);
      else if(key == "HeartbeatIntervalSeconds")  cfg_HeartbeatIntervalSeconds = (int)StringToInteger(value);
      else if(key == "ReconnectIntervalSeconds")  cfg_ReconnectIntervalSeconds = (int)StringToInteger(value);
      else if(key == "MaxReconnectAttempts")       cfg_MaxReconnectAttempts = (int)StringToInteger(value);
      else if(key == "LogUploadIntervalSeconds")  cfg_LogUploadIntervalSeconds = (int)StringToInteger(value);
      else if(key == "DebugMode")                 cfg_DebugMode = (int)StringToInteger(value);
   }
   
   FileClose(handle);
   Print("Configuration loaded from ", CONFIG_FILE);
   return true;
}

//+------------------------------------------------------------------+
//| Save current configuration to file (only called when generating default) |
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
   FileWriteString(handle, "# Auto-generated default configuration.\r\n");
   FileWriteString(handle, "# Feel free to edit this file to change EA settings without recompiling.\r\n");
   FileWriteString(handle, "ServerURL=" + cfg_ServerURL + "\r\n");
   FileWriteString(handle, "UserKey=" + cfg_UserKey + "\r\n");
   FileWriteString(handle, "UpdateIntervalSeconds=" + IntegerToString(cfg_UpdateIntervalSeconds) + "\r\n");
   FileWriteString(handle, "HeartbeatIntervalSeconds = " + IntegerToString(cfg_HeartbeatIntervalSeconds) + "\r\n");
   FileWriteString(handle, "ReconnectIntervalSeconds = " + IntegerToString(cfg_ReconnectIntervalSeconds) + "\r\n");
   FileWriteString(handle, "MaxReconnectAttempts = " + IntegerToString(cfg_MaxReconnectAttempts) + "\r\n");
   FileWriteString(handle, "LogUploadIntervalSeconds = " + IntegerToString(cfg_LogUploadIntervalSeconds) + "\r\n");
   FileWriteString(handle, "DebugMode = " + IntegerToString(cfg_DebugMode) + "\r\n");
   
   FileClose(handle);
   Print("Default configuration saved to ", CONFIG_FILE);
}

//+------------------------------------------------------------------+
//| Initialize config: load from file or use input defaults            |
//+------------------------------------------------------------------+
void InitConfig()
{
   if(LoadConfig())
   {
      Print("Loaded configuration from ", CONFIG_FILE);
      
      bool uiChanged = false;
      
      if(ServerURL != "" && ServerURL != "https://DEINE-DOMAIN.DE:8080" && ServerURL != cfg_ServerURL) uiChanged = true;
      if(InpUserKey != "" && InpUserKey != cfg_UserKey) uiChanged = true;
      if(UpdateIntervalSeconds != 5 && UpdateIntervalSeconds != cfg_UpdateIntervalSeconds) uiChanged = true;
      if(HeartbeatIntervalSeconds != 30 && HeartbeatIntervalSeconds != cfg_HeartbeatIntervalSeconds) uiChanged = true;
      if(ReconnectIntervalSeconds != 30 && ReconnectIntervalSeconds != cfg_ReconnectIntervalSeconds) uiChanged = true;
      if(MaxReconnectAttempts != 10 && MaxReconnectAttempts != cfg_MaxReconnectAttempts) uiChanged = true;
      if(LogUploadIntervalSeconds != 300 && LogUploadIntervalSeconds != cfg_LogUploadIntervalSeconds) uiChanged = true;
      if(DebugMode != 0 && DebugMode != cfg_DebugMode) uiChanged = true;
      
      if(cfg_LogUploadIntervalSeconds == 0 && LogUploadIntervalSeconds == 300)
      {
         cfg_LogUploadIntervalSeconds = 300;
         uiChanged = true;
      }
      
      if(uiChanged)
      {
         Print("UI inputs differ from config file. Updating config file with new UI values.");
         cfg_ServerURL = (ServerURL != "" && ServerURL != "https://DEINE-DOMAIN.DE:8080") ? ServerURL : cfg_ServerURL;
         cfg_UserKey = (InpUserKey != "") ? InpUserKey : cfg_UserKey;
         cfg_UpdateIntervalSeconds = (UpdateIntervalSeconds != 15) ? UpdateIntervalSeconds : cfg_UpdateIntervalSeconds;
         cfg_HeartbeatIntervalSeconds = (HeartbeatIntervalSeconds != 30) ? HeartbeatIntervalSeconds : cfg_HeartbeatIntervalSeconds;
         cfg_ReconnectIntervalSeconds = (ReconnectIntervalSeconds != 30) ? ReconnectIntervalSeconds : cfg_ReconnectIntervalSeconds;
         cfg_MaxReconnectAttempts = (MaxReconnectAttempts != 10) ? MaxReconnectAttempts : cfg_MaxReconnectAttempts;
         cfg_LogUploadIntervalSeconds = (LogUploadIntervalSeconds != 300) ? LogUploadIntervalSeconds : cfg_LogUploadIntervalSeconds;
         cfg_DebugMode = (DebugMode != 0) ? DebugMode : cfg_DebugMode;
         
         SaveConfig();
      }
   }
   else
   {
      cfg_ServerURL = ServerURL;
      cfg_UserKey = InpUserKey;
      cfg_UpdateIntervalSeconds = UpdateIntervalSeconds;
      cfg_HeartbeatIntervalSeconds = HeartbeatIntervalSeconds;
      cfg_ReconnectIntervalSeconds = ReconnectIntervalSeconds;
      cfg_MaxReconnectAttempts = MaxReconnectAttempts;
      cfg_LogUploadIntervalSeconds = LogUploadIntervalSeconds;
      cfg_DebugMode = DebugMode;
      Print("Config file not found. Using UI inputs and generating a default config file.");
      
      SaveConfig();
   }
   
   StringTrimRight(cfg_ServerURL);
   StringTrimLeft(cfg_ServerURL);
   int urlLen = StringLen(cfg_ServerURL);
   if(urlLen > 0 && StringGetCharacter(cfg_ServerURL, urlLen - 1) == '/')
      cfg_ServerURL = StringSubstr(cfg_ServerURL, 0, urlLen - 1);
   urlLen = StringLen(cfg_ServerURL);
   if(urlLen > 0 && StringGetCharacter(cfg_ServerURL, urlLen - 1) == 13)
      cfg_ServerURL = StringSubstr(cfg_ServerURL, 0, urlLen - 1);
   
   Print("Active config: ServerURL=", cfg_ServerURL,
         " (len=", StringLen(cfg_ServerURL), ")",
         " UpdateInterval=", cfg_UpdateIntervalSeconds,
         " HeartbeatInterval=", cfg_HeartbeatIntervalSeconds,
         " ReconnectInterval=", cfg_ReconnectIntervalSeconds,
         " MaxReconnectAttempts=", cfg_MaxReconnectAttempts,
         " LogUploadInterval=", cfg_LogUploadIntervalSeconds);
   Print(">>> MT4 Allowed URL must be EXACTLY: ", cfg_ServerURL, " (copy this!)");
}

//+------------------------------------------------------------------+
//| Expert initialization function                                     |
//+------------------------------------------------------------------+
int OnInit()
{
   Print("TradeMonitorClient EA v" + EA_VERSION + " initialized");
   
   DiagnosticWebRequestTest();
   
   InitConfig();
   
   // Build unique GlobalVariable names per account
   string accStr = IntegerToString(AccountNumber());
   GV_TRADELIST_SENT = "TM_Trade_Sent_" + accStr;
   GV_LAST_LOG_LINES = "TM_LastLogLines_" + accStr;
   GV_LAST_LOG_DATE  = "TM_LastLogDate_" + accStr;
   GV_LAST_SYNC_TIME  = "TM_LastSync_" + accStr;
   
   // Load persisted states
   if(GlobalVariableCheck(GV_TRADELIST_SENT))
   {
      double val = GlobalVariableGet(GV_TRADELIST_SENT);
      if(val > 0)
      {
         g_tradeListSent = true;
         g_tradeListSentTime = (datetime)val;
      }
   }
   
   if(GlobalVariableCheck(GV_LAST_LOG_LINES))
   {
      g_lastLogLinesSent = (int)GlobalVariableGet(GV_LAST_LOG_LINES);
      if(g_lastLogLinesSent > 0)
         Print("Loaded last log position: line ", g_lastLogLinesSent);
   }
   
   LoadLastSyncTime();
   
   Print("Server URL: ", cfg_ServerURL);
   Print("Account ID: ", AccountInfoInteger(ACCOUNT_LOGIN));
      // Create reconnect button on chart
   CreateReconnectButton();
   CreateStatusLabel();
   
   // Register with server on startup
   UpdateStatusLabel("Connecting to Server...", clrWhite);
   if(RegisterWithServer())
   {
      isRegistered = true;
      g_lastError = 0;
      Print("Successfully registered with server");
      
      // Send full trade list on first connect (only if not already sent)
      if(!g_tradeListSent)
      {
         UpdateStatusLabel("Syncing Trade Data...", clrWhite);
         if(SendInitialTradeList())
         {
            g_tradeListSent = true;
            g_tradeListSentTime = TimeCurrent();
            GlobalVariableSet(GV_TRADELIST_SENT, (double)g_tradeListSentTime);
            Print("Initial trade list sent successfully at ", TimeToString(g_tradeListSentTime));
            UpdateStatusLabel("Connected\nID: " + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)), clrGreen);
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
   
   if(!IsTradeAllowed())
   {
      UpdateStatusLabel("Trading DISABLED\nHeartbeat stopped", clrRed);
      return;
   }
   
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
      
      if(now < g_nextRetryTime)
         return;
      
      g_reconnectAttempts++;
      
      if(g_extendedRetryMode)
         Print("Extended Retry attempt ", g_reconnectAttempts, " (Interval: 15 min)");
      else
         Print("Reconnect attempt ", g_reconnectAttempts, "/", cfg_MaxReconnectAttempts);
      
      if(RegisterWithServer())
      {
         isRegistered = true;
         g_extendedRetryMode = false;
         g_reconnectAttempts = 0;
         g_lastError = 0;
         
         statusText = "Connected\nID: " + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN));
         UpdateStatusLabel(statusText, clrGreen);
         
         Print("Successfully registered with server");
         
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
         g_lastError = GetLastError();
         
         if(!g_extendedRetryMode && cfg_MaxReconnectAttempts > 0 && g_reconnectAttempts >= cfg_MaxReconnectAttempts)
         {
            g_extendedRetryMode = true;
            g_reconnectAttempts = 0;
            Print("Max normal reconnect attempts reached. Switching to 15-minute extended retry mode.");
            g_nextRetryTime = now + LONG_RETRY_INTERVAL;
         }
         else
         {
            int interval = g_extendedRetryMode ? LONG_RETRY_INTERVAL : cfg_ReconnectIntervalSeconds;
            g_nextRetryTime = now + interval;
            Print("Reconnect failed. Next attempt in ", interval, " seconds.");
         }
      }
      return;
   }
   
   string statusText = "Connected\nID: " + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN));
   if(g_tradeListSent)
      statusText += "\nSync: OK";
   else
      statusText += "\nSync: Pending";
      
   UpdateStatusLabel(statusText, clrGreen);
   
   if(g_tradeListSent)
   {
      if(currentTick - lastUpdateTick >= (uint)cfg_UpdateIntervalSeconds * 1000)
      {
         SendTradeUpdate();
         SendHistoryUpdate();
         lastUpdateTick = currentTick;
      }
      
      if(currentTick - lastHeartbeatTick >= (uint)cfg_HeartbeatIntervalSeconds * 1000)
      {
         SendHeartbeat();
         lastHeartbeatTick = currentTick;
      }
   }
   else
   {
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

   // Send EA logs ALWAYS when connected (independent of trade sync status)
   if(cfg_LogUploadIntervalSeconds > 0 && currentTick - lastLogUploadTick >= (uint)cfg_LogUploadIntervalSeconds * 1000)
   {
      SendEaLogs();
      lastLogUploadTick = currentTick;
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
   ObjectSetString(0, LBL_STATUS, OBJPROP_TEXT, "v" + EA_VERSION + " | " + text + " | URL: " + cfg_ServerURL);
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
         lastLogUploadTick = 0; // Force immediate log upload on reconnect
         g_lastLogLinesSent = 0; // Reset log line counter to re-send all lines
         GlobalVariableSet(GV_LAST_LOG_LINES, 0); // Persist the reset
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
   UpdateStatusLabel("Syncing Open Trades...", clrWhite);
   string tradesJson = BuildOpenTradesJson();
   
   // Build closed trades array (full history)
   Print("Building closed trades JSON (full history)...");
   UpdateStatusLabel("Syncing Trade History...", clrWhite);
   string latestCloseTime = "";
   string historyJson = BuildClosedTradesJson("", latestCloseTime);
   
   UpdateStatusLabel("Uploading Data to Server...", clrWhite);
   
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
             tradesJson += "\"commission\":" + DoubleToString(OrderCommission(), 2) + ",";
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
   if(!isIncremental && cfg_DebugMode > 0)
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
                    if(cfg_DebugMode > 0)
                       Print("History progress: ", closedCount, " closed trades processed (", pct, "% scanned)");
                    UpdateStatusLabel("Reading History: " + IntegerToString(closedCount) + " trades (" + IntegerToString(pct) + "%)", clrWhite);
                 }
             }
         }
      }
   }
   historyJson += "]";
   if(isIncremental)
   {
      if(closedCount > 0 && cfg_DebugMode > 0)
         Print("Incremental sync: ", closedCount, " new trades (skipped ", skippedCount, " already synced)");
   }
   else if(cfg_DebugMode > 0)
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
   if (StringLen(cfg_UserKey) > 0) {
       headers += "X-User-Key: " + cfg_UserKey + "\r\n";
   }
   
   ResetLastError();
   
   // Debug: print exact URL before sending
   if(cfg_DebugMode > 0)
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
//| Send EA log entries to server (incremental)                        |
//+------------------------------------------------------------------+
void SendEaLogs()
{
   string logDate = TimeToString(TimeLocal(), TIME_DATE);
   StringReplace(logDate, ".", "");
   double currentDateNumeric = StringToDouble(logDate);
   
   // Check if the day rolled over. If so, reset the line counter.
   if(GlobalVariableCheck(GV_LAST_LOG_DATE))
   {
      double lastDate = GlobalVariableGet(GV_LAST_LOG_DATE);
      if(lastDate > 0 && lastDate != currentDateNumeric)
      {
         // Day has changed! Reset line count.
         g_lastLogLinesSent = 0;
         GlobalVariableSet(GV_LAST_LOG_LINES, 0);
         GlobalVariableSet(GV_LAST_LOG_DATE, currentDateNumeric);
         Print("EA Logs Sync: New day detected (", logDate, "), resetting log line counter to 0");
      }
   }
   else
   {
      GlobalVariableSet(GV_LAST_LOG_DATE, currentDateNumeric);
   }
   
   string srcPath = TerminalInfoString(TERMINAL_DATA_PATH) + "\\logs\\" + logDate + ".log";
   string destFile = "ea_log_copy.txt";
   string destPath = TerminalInfoString(TERMINAL_DATA_PATH) + "\\MQL4\\Files\\" + destFile;
   
   if(!CopyFileW(srcPath, destPath, false))
   {
      Print("EA Logs Sync: Failed to copy log file. Ensure 'Allow DLL imports' is enabled.");
      return;
   }
   
   int handle = FileOpen(destFile, FILE_READ|FILE_TXT|FILE_ANSI|FILE_SHARE_READ);
   if(handle == INVALID_HANDLE)
   {
      return;
   }
   
   string lines[];
   int lineCount = 0;
   
   while(!FileIsEnding(handle))
   {
      string line = FileReadString(handle);
      if(StringLen(line) > 0)
      {
         lineCount++;
         if(lineCount > g_lastLogLinesSent)
         {
            int newIdx = ArraySize(lines);
            ArrayResize(lines, newIdx + 1);
            lines[newIdx] = line;
         }
      }
   }
   FileClose(handle);
   
   int newLines = ArraySize(lines);
   if(newLines == 0)
      return;
   
   int maxLines = MathMin(newLines, 500);
   
   string logsJson = "[";
   for(int i = 0; i < maxLines; i++)
   {
      if(i > 0) logsJson += ",";
      logsJson += "\"" + EscapeJson(lines[i]) + "\"";
   }
   logsJson += "]";
   
   string json = "{";
   json += "\"accountId\":" + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)) + ",";
   json += "\"logEntries\":" + logsJson + ",";
   json += "\"timestamp\":\"" + TimeToString(TimeCurrent(), TIME_DATE|TIME_SECONDS) + "\"";
   json += "}";
   
   string url = cfg_ServerURL + "/api/ea-logs";
   
   if(SendHttpPost(url, json))
   {
      g_lastLogLinesSent += maxLines;
      GlobalVariableSet(GV_LAST_LOG_LINES, (double)g_lastLogLinesSent);
      if(cfg_DebugMode > 0)
         Print("EA logs sent: ", maxLines, " new lines (total tracked: ", g_lastLogLinesSent, ")");
   }
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
