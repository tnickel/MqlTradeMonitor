//+------------------------------------------------------------------+
//|                                         TradeMonitorClient.mq5   |
//|                                    Trade Monitor Client EA       |
//|                        Sends trades to monitoring server         |
//+------------------------------------------------------------------+
#property copyright "TradeMonitor"
#property version   "1.12"
#property strict

//--- Input parameters (defaults, overridden by config file if present)
input string   ServerURL = "https://monitor.tnickel-ki.de"; // Server URL (use HTTPS!)
input string   InpUserKey = "jILus66S1hLrd8m0i_pgoiCQIc6JuA3asfM328UGFQ4"; // User API-Key (from Admin Dashboard)
input int      UpdateIntervalSeconds = 15;             // Update interval (seconds)
input int      HeartbeatIntervalSeconds = 30;          // Heartbeat interval (seconds)
input int      ReconnectIntervalSeconds = 30;          // Reconnect interval (seconds)
input int      MaxReconnectAttempts = 10;               // Max reconnect attempts (0=unlimited)
input int      LogUploadIntervalSeconds = 300;         // EA Log upload interval (seconds, 0=disabled)
input int      DebugMode = 0;                          // Debug Logging (0=Off, 1=On)

//--- DLL imports
#import "kernel32.dll"
bool CopyFileW(string lpExistingFileName, string lpNewFileName, bool bFailIfExists);
#import

//--- Config file name (stored in MQL5/Files/)
#define CONFIG_FILE "TradeMonitorClient.cfg"
#define EA_VERSION "1.12"

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
int httpTimeoutInit = 300000;    // 300 seconds (5 min) timeout for initial trade list (large payload)

//--- Trade list tracking
bool g_tradeListSent = false;            // Whether full trade list was successfully sent
datetime g_tradeListSentTime = 0;        // When it was sent
uint g_lastInitRetryTime = 0;            // Cooldown tracker for init retries (ms ticks)
int g_initRetryCount = 0;                // Number of init trade list retry attempts
#define MAX_INIT_RETRIES 5               // Max retries before giving up

//--- History sync tracking
string g_lastSyncedCloseTime = "";       // Last close time successfully synced to server

//--- EA Log tracking
int g_lastLogLinesSent = 0;              // Number of log lines already sent to server
string GV_TRADELIST_SENT = "";           // GlobalVariable name for persisting trade list sync status
string GV_LAST_LOG_LINES = "";           // GlobalVariable name for persisting log position
string GV_LAST_LOG_DATE = "";            // GlobalVariable name for persisting the log date format YYYYMMDD

//--- Reconnect tracking
int g_reconnectAttempts = 0;             // Number of reconnect attempts
bool g_extendedRetryMode = false;        // Whether we are in the 15-minute extended retry phase
datetime g_nextRetryTime = 0;            // Timestamp for next retry attempt
int g_lastError = 0;                     // Last web request error
bool g_authFailed = false;               // Flag when API key is invalid/unauthorized (HTTP 401/403)
bool g_invalidConfig = false;            // Flag when ServerURL or config is invalid

//--- Constants
#define LONG_RETRY_INTERVAL 900          // 15 minutes (900 seconds) for long retry mode

//--- Button and Label constants
#define BTN_RECONNECT "btnReconnectServer"
#define LBL_STATUS "lblStatus"

//--- MetaTrader GlobalVariable names for persisting state

void FlushRemainingLogs(string srcPath);
bool SendLogLinesChunk(string &lines[], int startIdx, int count);
string EscapeJson(string text);
bool SendHttpPost(string url, string json);
bool SendHttpPostWithTimeout(string url, string json, int timeout);
void UpdateStatusLabel(string text, color col);
void SaveLastSyncTime(string closeTime);
bool RegisterWithServer();
bool SendInitialTradeList();
string GetTicksJson(string symbol, long timeMsc);

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
      
      if(key == "") continue;
      
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
   FileWriteString(handle, "HeartbeatIntervalSeconds=" + IntegerToString(cfg_HeartbeatIntervalSeconds) + "\r\n");
   FileWriteString(handle, "ReconnectIntervalSeconds=" + IntegerToString(cfg_ReconnectIntervalSeconds) + "\r\n");
   FileWriteString(handle, "MaxReconnectAttempts=" + IntegerToString(cfg_MaxReconnectAttempts) + "\r\n");
   FileWriteString(handle, "LogUploadIntervalSeconds=" + IntegerToString(cfg_LogUploadIntervalSeconds) + "\r\n");
   FileWriteString(handle, "DebugMode=" + IntegerToString(cfg_DebugMode) + "\r\n");
   
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
      
      bool uiChanged = (UninitializeReason() == REASON_PARAMETERS);
      
      if(!uiChanged)
      {
         // If starting fresh (reason 0), compare inputs with loaded config.
         // Only overwrite if UI input is NOT the default value and is different from config.
         if(ServerURL != "https://monitor.tnickel-ki.de" && ServerURL != cfg_ServerURL) { cfg_ServerURL = ServerURL; uiChanged = true; }
         if(InpUserKey != "jILus66S1hLrd8m0i_pgoiCQIc6JuA3asfM328UGFQ4" && InpUserKey != cfg_UserKey) { cfg_UserKey = InpUserKey; uiChanged = true; }
         if(UpdateIntervalSeconds != 15 && UpdateIntervalSeconds != cfg_UpdateIntervalSeconds) { cfg_UpdateIntervalSeconds = UpdateIntervalSeconds; uiChanged = true; }
         if(HeartbeatIntervalSeconds != 30 && HeartbeatIntervalSeconds != cfg_HeartbeatIntervalSeconds) { cfg_HeartbeatIntervalSeconds = HeartbeatIntervalSeconds; uiChanged = true; }
         if(ReconnectIntervalSeconds != 30 && ReconnectIntervalSeconds != cfg_ReconnectIntervalSeconds) { cfg_ReconnectIntervalSeconds = ReconnectIntervalSeconds; uiChanged = true; }
         if(MaxReconnectAttempts != 10 && MaxReconnectAttempts != cfg_MaxReconnectAttempts) { cfg_MaxReconnectAttempts = MaxReconnectAttempts; uiChanged = true; }
         if(LogUploadIntervalSeconds != 300 && LogUploadIntervalSeconds != cfg_LogUploadIntervalSeconds) { cfg_LogUploadIntervalSeconds = LogUploadIntervalSeconds; uiChanged = true; }
         if(DebugMode != 0 && DebugMode != cfg_DebugMode) { cfg_DebugMode = DebugMode; uiChanged = true; }
      }
      else
      {
         // Parameter changed on running EA: copy all values directly
         cfg_ServerURL = ServerURL;
         cfg_UserKey = InpUserKey;
         cfg_UpdateIntervalSeconds = UpdateIntervalSeconds;
         cfg_HeartbeatIntervalSeconds = HeartbeatIntervalSeconds;
         cfg_ReconnectIntervalSeconds = ReconnectIntervalSeconds;
         cfg_MaxReconnectAttempts = MaxReconnectAttempts;
         cfg_LogUploadIntervalSeconds = LogUploadIntervalSeconds;
         cfg_DebugMode = DebugMode;
      }
      
      if(uiChanged)
      {
         Print("UI inputs differ from configuration or parameters changed. Updating config file.");
         SaveConfig();
      }
   }
   else
   {
      // No config file, use current UI input defaults
      cfg_ServerURL = ServerURL;
      cfg_UserKey = InpUserKey;
      cfg_UpdateIntervalSeconds = UpdateIntervalSeconds;
      cfg_HeartbeatIntervalSeconds = HeartbeatIntervalSeconds;
      cfg_ReconnectIntervalSeconds = ReconnectIntervalSeconds;
      cfg_MaxReconnectAttempts = MaxReconnectAttempts;
      cfg_LogUploadIntervalSeconds = LogUploadIntervalSeconds;
      cfg_DebugMode = DebugMode;
      Print("Config file not found. Using UI inputs and generating a default config file.");
      
      // Generate the default configuration file so the user has something to edit
      SaveConfig();
   }
   
   // Normalize URL: trim whitespace, remove trailing slash and \r
   StringTrimRight(cfg_ServerURL);
   StringTrimLeft(cfg_ServerURL);
   int urlLen = StringLen(cfg_ServerURL);
   if(urlLen > 0 && StringGetCharacter(cfg_ServerURL, urlLen - 1) == '/')
      cfg_ServerURL = StringSubstr(cfg_ServerURL, 0, urlLen - 1);
   urlLen = StringLen(cfg_ServerURL);
   if(urlLen > 0 && StringGetCharacter(cfg_ServerURL, urlLen - 1) == 13)
      cfg_ServerURL = StringSubstr(cfg_ServerURL, 0, urlLen - 1);
   
   if(StringLen(cfg_ServerURL) < 8 || StringFind(cfg_ServerURL, "http") != 0)
   {
      Print("CRITICAL ERROR: ServerURL '", cfg_ServerURL, "' is invalid. It must start with http:// or https://.");
      g_invalidConfig = true;
   }
   
   // Enforce interval clamps
   if(cfg_UpdateIntervalSeconds < 15) cfg_UpdateIntervalSeconds = 15;
   if(cfg_UpdateIntervalSeconds > 3600) cfg_UpdateIntervalSeconds = 3600;
   
   if(cfg_HeartbeatIntervalSeconds < 30) cfg_HeartbeatIntervalSeconds = 30;
   if(cfg_HeartbeatIntervalSeconds > 3600) cfg_HeartbeatIntervalSeconds = 3600;
   
   if(cfg_ReconnectIntervalSeconds < 30) cfg_ReconnectIntervalSeconds = 30;
   if(cfg_ReconnectIntervalSeconds > 3600) cfg_ReconnectIntervalSeconds = 3600;
   
   if(cfg_LogUploadIntervalSeconds > 0 && cfg_LogUploadIntervalSeconds < 30) cfg_LogUploadIntervalSeconds = 30;
   if(cfg_LogUploadIntervalSeconds > 86400) cfg_LogUploadIntervalSeconds = 86400;

   Print("Active config: ServerURL=", cfg_ServerURL,
         " UpdateInterval=", cfg_UpdateIntervalSeconds,
         " HeartbeatInterval=", cfg_HeartbeatIntervalSeconds,
         " ReconnectInterval=", cfg_ReconnectIntervalSeconds,
         " LogUploadInterval=", cfg_LogUploadIntervalSeconds,
         " MaxReconnectAttempts=", cfg_MaxReconnectAttempts,
         " DebugMode=", cfg_DebugMode);
}

//+------------------------------------------------------------------+
//| Expert initialization function                                     |
//+------------------------------------------------------------------+
int OnInit()
{
   Print("TradeMonitorClient EA v" + EA_VERSION + " initialized");
   
   g_authFailed = false;
   g_invalidConfig = false;
   // Load or initialize configuration
   InitConfig();
   
   // Build unique GlobalVariable names per account
   string accStr = IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN));
   GV_TRADELIST_SENT = "TM_Trade_Sent_" + accStr;
   GV_LAST_LOG_LINES = "TM_LastLogLines_" + accStr;
   GV_LAST_LOG_DATE  = "TM_LastLogDate_" + accStr;
    
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
      if(g_lastLogLinesSent > 0 && cfg_DebugMode > 0)
         Print("Loaded last log position: line ", g_lastLogLinesSent);
   }
   
   // Load last synced close time from config file
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
            UpdateStatusLabel("Connected\nID: " + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)), clrLime);
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
   if(g_invalidConfig)
   {
      UpdateStatusLabel("CRITICAL: Invalid Server URL\nUpdates stopped", clrRed);
      return;
   }
   
   if(g_authFailed)
   {
      UpdateStatusLabel("CRITICAL: API Key Invalid (401/403)\nUpdates stopped", clrRed);
      return;
   }

   // Check if the terminal is actually connected to the broker
   if(!TerminalInfoInteger(TERMINAL_CONNECTED))
   {
      UpdateStatusLabel("Disconnected from Broker", clrRed);
      return;
   }
   
   // Check if trading is allowed in the terminal
   // If trading is disabled, stop ALL communication including heartbeat
   // This will cause the server tile to go red (timeout)
   if(!TerminalInfoInteger(TERMINAL_TRADE_ALLOWED))
   {
      UpdateStatusLabel("Trading DISABLED\nHeartbeat stopped", clrRed);
      return;
   }

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
         UpdateStatusLabel(statusText, clrLime);
         
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
      
   UpdateStatusLabel(statusText, clrLime);
   
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
   ObjectSetString(0, LBL_STATUS, OBJPROP_TEXT, "MQL5 Client v" + EA_VERSION + ": Initializing...");
   ObjectSetInteger(0, LBL_STATUS, OBJPROP_COLOR, clrWhite);
   ObjectSetInteger(0, LBL_STATUS, OBJPROP_FONTSIZE, 13);
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
         g_authFailed = false;
         g_invalidConfig = false;
         g_reconnectAttempts = 0;
         g_extendedRetryMode = false;
         g_nextRetryTime = 0;
         UpdateStatusLabel("MQL5 Client: Connection setup", clrOrange);
         g_tradeListSent = false;
         g_tradeListSentTime = 0;
         g_lastInitRetryTime = 0;
         g_initRetryCount = 0;
         g_lastSyncedCloseTime = "";
         isRegistered = false;
         lastReconnectAttemptTick = 0;
         lastLogUploadTick = 0; // Force immediate log upload on reconnect (incremental)
         GlobalVariableSet(GV_TRADELIST_SENT, 0);  // Clear persisted status
         SaveLastSyncTime("");                     // Clear sync bookmark
         
         // Try immediate reconnect
         if(RegisterWithServer())
         {
            isRegistered = true;
            g_extendedRetryMode = false;
            UpdateStatusLabel("MQL5 Client: Connected", clrLime);
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
            UpdateStatusLabel("MQL5 Client: Connection setup", clrOrange);
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
   long login = AccountInfoInteger(ACCOUNT_LOGIN);
   if(login == 0)
   {
      Print("Warning: ACCOUNT_LOGIN is 0. Terminal is not logged in yet. Registration skipped.");
      return false;
   }
   string url = cfg_ServerURL + "/api/register";
   
   // Build JSON payload
   string json = "{";
   json += "\"accountId\":" + IntegerToString(login) + ",";
   json += "\"broker\":\"" + EscapeJson(AccountInfoString(ACCOUNT_COMPANY)) + "\",";
   json += "\"currency\":\"" + EscapeJson(AccountInfoString(ACCOUNT_CURRENCY)) + "\",";
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
   if(cfg_DebugMode > 0) Print("Building open trades JSON...");
   UpdateStatusLabel("Syncing Open Trades...", clrWhite);
   string tradesJson = BuildOpenTradesJson();
   
   // Build closed trades array (full history)
   if(cfg_DebugMode > 0) Print("Building closed trades JSON (full history)...");
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
      // If no closed trades exist yet (e.g. new account with only a balance entry),
      // set the sync time to "now" so future SendHistoryUpdate() calls run in
      // incremental mode and don't spam the log with full-history scans.
      if(latestCloseTime == "")
         latestCloseTime = TimeToString(TimeCurrent() - 1, TIME_DATE|TIME_SECONDS);
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
         tradesJson += "\"symbol\":\"" + EscapeJson(PositionGetString(POSITION_SYMBOL)) + "\",";
         tradesJson += "\"type\":\"" + (PositionGetInteger(POSITION_TYPE) == POSITION_TYPE_BUY ? "BUY" : "SELL") + "\",";
         tradesJson += "\"volume\":" + DoubleToString(PositionGetDouble(POSITION_VOLUME), 2) + ",";
         tradesJson += "\"openPrice\":" + DoubleToString(PositionGetDouble(POSITION_PRICE_OPEN), 5) + ",";
         tradesJson += "\"openTime\":\"" + TimeToString((datetime)PositionGetInteger(POSITION_TIME), TIME_DATE|TIME_SECONDS) + "\",";
         tradesJson += "\"stopLoss\":" + DoubleToString(PositionGetDouble(POSITION_SL), 5) + ",";
         tradesJson += "\"takeProfit\":" + DoubleToString(PositionGetDouble(POSITION_TP), 5) + ",";
         tradesJson += "\"profit\":" + DoubleToString(PositionGetDouble(POSITION_PROFIT), 2) + ",";
         tradesJson += "\"swap\":" + DoubleToString(PositionGetDouble(POSITION_SWAP), 2) + ",";
         tradesJson += "\"commission\":" + DoubleToString(PositionGetDouble(POSITION_COMMISSION), 2) + ",";
         tradesJson += "\"magicNumber\":" + IntegerToString(PositionGetInteger(POSITION_MAGIC)) + ",";
         tradesJson += "\"comment\":\"" + EscapeJson(PositionGetString(POSITION_COMMENT)) + "\"";
         tradesJson += "}";
      }
   }
   tradesJson += "]";
   return tradesJson;
}

//+------------------------------------------------------------------+
//| Get market Bid and Ask for a symbol at a specific millisecond    |
//+------------------------------------------------------------------+
void GetMarketBidAsk(string symbol, long timeMsc, double &bid, double &ask)
{
   bid = 0.0;
   ask = 0.0;
   
   MqlTick ticks[];
   // Copy ticks in a 4-second window centered at timeMsc
   int copied = CopyTicksRange(symbol, ticks, COPY_TICKS_ALL, timeMsc - 2000, timeMsc + 2000);
   if(copied > 0)
   {
      long minDiff = 99999999;
      int bestIdx = -1;
      for(int k = 0; k < copied; k++)
      {
         long diff = MathAbs(ticks[k].time_msc - timeMsc);
         if(diff < minDiff)
         {
            minDiff = diff;
            bestIdx = k;
         }
      }
      if(bestIdx != -1)
      {
         bid = ticks[bestIdx].bid;
         ask = ticks[bestIdx].ask;
      }
   }
}

//+------------------------------------------------------------------+
//| Get tick history around a specific millisecond as JSON           |
//+------------------------------------------------------------------+
string GetTicksJson(string symbol, long timeMsc)
{
   MqlTick ticks[];
   // Copy ticks in a 4-second window centered at timeMsc
   int copied = CopyTicksRange(symbol, ticks, COPY_TICKS_ALL, timeMsc - 2000, timeMsc + 2000);
   if(copied <= 0) return "[]";
   
   string json = "[";
   for(int i = 0; i < copied; i++)
   {
      if(i > 0) json += ",";
      json += "[" + IntegerToString(ticks[i].time_msc) + "," + 
              DoubleToString(ticks[i].bid, 5) + "," + 
              DoubleToString(ticks[i].ask, 5) + "," + 
              DoubleToString(ticks[i].volume_real, 2) + "]";
   }
   json += "]";
   return json;
}

//+------------------------------------------------------------------+
//| Get historical order setup time in milliseconds                 |
//+------------------------------------------------------------------+
long GetOrderSetupTimeMsc(ulong orderTicket)
{
   if(orderTicket > 0 && HistoryOrderSelect(orderTicket))
   {
      long type = HistoryOrderGetInteger(orderTicket, ORDER_TYPE);
      if(type == ORDER_TYPE_BUY || type == ORDER_TYPE_SELL)
      {
         return HistoryOrderGetInteger(orderTicket, ORDER_TIME_SETUP_MSC);
      }
   }
   return 0;
}

//+------------------------------------------------------------------+
//| Build JSON array of closed trades (history)                        |
//+------------------------------------------------------------------+
string BuildClosedTradesJson(string sinceCloseTime, string &outLatestCloseTime)
{
   // Determine incremental vs full sync BEFORE selecting history
   bool isIncremental = (sinceCloseTime != "" && StringLen(sinceCloseTime) > 0);
   datetime sinceTimeVal = 0;
   if(isIncremental)
   {
      sinceTimeVal = StringToTime(sinceCloseTime) - 7200; // 2 hours safety window
   }
   
   // For incremental updates, only load deals from the relevant time range.
   // For full sync, load entire history since year 2000.
   datetime fromDate = isIncremental ? sinceTimeVal : D'2000.01.01 00:00:00';
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
   
   if(!isIncremental && cfg_DebugMode > 0)
      Print("Processing full history: ", totalDeals, " deals found...");
   
   for(int i = 0; i < totalDeals; i++)
   {
      ulong ticket = HistoryDealGetTicket(i);
      if(ticket > 0)
      {
         ENUM_DEAL_ENTRY entry = (ENUM_DEAL_ENTRY)HistoryDealGetInteger(ticket, DEAL_ENTRY);
         if(entry == DEAL_ENTRY_OUT || entry == DEAL_ENTRY_OUT_BY)
         {
            datetime dealTimeVal = (datetime)HistoryDealGetInteger(ticket, DEAL_TIME);
            string closeTime = TimeToString(dealTimeVal, TIME_DATE|TIME_SECONDS);
            
            // Skip trades already synced (incremental mode) using the 2-hour safety window
            if(isIncremental && dealTimeVal < sinceTimeVal)
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
            
            // Determine the original trade direction and total commission by finding the ENTRY_IN deal
            // for this position via DEAL_POSITION_ID. Optimized to search backwards from i-1 to 0 (O(N) total runtime).
            string symbol = HistoryDealGetString(ticket, DEAL_SYMBOL);
            string typeStr = "UNKNOWN";
            double openPrice = HistoryDealGetDouble(ticket, DEAL_PRICE); // fallback: OUT deal price
            string openTime = closeTime; // fallback: same as close time
            long openTimeMsc = dealTimeVal * 1000; // fallback: close time in ms
            long magicNumber = HistoryDealGetInteger(ticket, DEAL_MAGIC); // fallback: OUT deal magic number
            double totalCommission = HistoryDealGetDouble(ticket, DEAL_COMMISSION); // start with OUT deal commission
            string tradeComment = HistoryDealGetString(ticket, DEAL_COMMENT);
            long openOrderSetupTimeMsc = 0;
            double openBid = 0.0;
            double openAsk = 0.0;
            
            long positionId = (long)HistoryDealGetInteger(ticket, DEAL_POSITION_ID);
            if(positionId > 0)
            {
               for(int j = i - 1; j >= 0; j--)
               {
                  ulong otherTicket = HistoryDealGetTicket(j);
                  if(otherTicket > 0 && otherTicket != ticket 
                     && HistoryDealGetInteger(otherTicket, DEAL_POSITION_ID) == positionId)
                  {
                     // Find the IN deal for open data and its commission
                     if((ENUM_DEAL_ENTRY)HistoryDealGetInteger(otherTicket, DEAL_ENTRY) == DEAL_ENTRY_IN)
                     {
                        // Proportional allocation of IN deal commission to avoid double-counting on partial closes
                        double inVol = HistoryDealGetDouble(otherTicket, DEAL_VOLUME);
                        double outVol = HistoryDealGetDouble(ticket, DEAL_VOLUME);
                        double proportion = (inVol > 0) ? (outVol / inVol) : 1.0;
                        if(proportion > 1.0) proportion = 1.0;
                        totalCommission += HistoryDealGetDouble(otherTicket, DEAL_COMMISSION) * proportion;
                        
                        ENUM_DEAL_TYPE entryType = (ENUM_DEAL_TYPE)HistoryDealGetInteger(otherTicket, DEAL_TYPE);
                        typeStr = (entryType == DEAL_TYPE_BUY) ? "BUY" : "SELL";
                        openPrice = HistoryDealGetDouble(otherTicket, DEAL_PRICE);
                        openTime = TimeToString((datetime)HistoryDealGetInteger(otherTicket, DEAL_TIME), TIME_DATE|TIME_SECONDS);
                        openTimeMsc = HistoryDealGetInteger(otherTicket, DEAL_TIME_MSC);
                        
                        ulong openOrderTicket = HistoryDealGetInteger(otherTicket, DEAL_ORDER);
                        openOrderSetupTimeMsc = GetOrderSetupTimeMsc(openOrderTicket);
                        
                        // Get magic number from the IN deal, as OUT deals (TP/SL) often have magic = 0
                        long inMagic = HistoryDealGetInteger(otherTicket, DEAL_MAGIC);
                        if(inMagic > 0) magicNumber = inMagic;
                        
                        string inComment = HistoryDealGetString(otherTicket, DEAL_COMMENT);
                        if(StringLen(inComment) > 0) tradeComment = inComment;
                        break; // Found matching IN deal, exit loop
                     }
                  }
               }
            }
             // Fallback: if no IN deal found, use the OUT deal type directly
            if(typeStr == "UNKNOWN")
            {
               ENUM_DEAL_TYPE dealType = (ENUM_DEAL_TYPE)HistoryDealGetInteger(ticket, DEAL_TYPE);
               typeStr = (dealType == DEAL_TYPE_BUY) ? "BUY" : "SELL";
            }
            
            long closeTimeMsc = HistoryDealGetInteger(ticket, DEAL_TIME_MSC);
            ulong closeOrderTicket = HistoryDealGetInteger(ticket, DEAL_ORDER);
            long closeOrderSetupTimeMsc = GetOrderSetupTimeMsc(closeOrderTicket);
            double closeBid = 0.0;
            double closeAsk = 0.0;
            
            string openTicksJson = "[]";
            string closeTicksJson = "[]";
            
            // Query market ticks for recent trades to analyze slippage (limit to last 7 days for full history scan)
            datetime currentMqlTime = TimeCurrent();
            if(isIncremental || dealTimeVal >= currentMqlTime - 7 * 24 * 3600)
            {
               GetMarketBidAsk(symbol, openTimeMsc, openBid, openAsk);
               GetMarketBidAsk(symbol, closeTimeMsc, closeBid, closeAsk);
               openTicksJson = GetTicksJson(symbol, openTimeMsc);
               closeTicksJson = GetTicksJson(symbol, closeTimeMsc);
            }
            
            historyJson += "{";
            historyJson += "\"ticket\":" + IntegerToString(ticket) + ",";
            historyJson += "\"symbol\":\"" + EscapeJson(symbol) + "\",";
            historyJson += "\"type\":\"" + typeStr + "\",";
            historyJson += "\"volume\":" + DoubleToString(HistoryDealGetDouble(ticket, DEAL_VOLUME), 2) + ",";
            historyJson += "\"openPrice\":" + DoubleToString(openPrice, 5) + ",";
            historyJson += "\"closePrice\":" + DoubleToString(HistoryDealGetDouble(ticket, DEAL_PRICE), 5) + ",";
            historyJson += "\"openTime\":\"" + openTime + "\",";
            historyJson += "\"closeTime\":\"" + closeTime + "\",";
            historyJson += "\"profit\":" + DoubleToString(HistoryDealGetDouble(ticket, DEAL_PROFIT), 2) + ",";
            historyJson += "\"swap\":" + DoubleToString(HistoryDealGetDouble(ticket, DEAL_SWAP), 2) + ",";
            historyJson += "\"commission\":" + DoubleToString(totalCommission, 2) + ",";
            historyJson += "\"magicNumber\":" + IntegerToString(magicNumber) + ",";
            historyJson += "\"comment\":\"" + EscapeJson(tradeComment) + "\",";
            historyJson += "\"openTimeMsc\":" + IntegerToString(openTimeMsc) + ",";
            historyJson += "\"closeTimeMsc\":" + IntegerToString(closeTimeMsc) + ",";
            historyJson += "\"openAsk\":" + DoubleToString(openAsk, 5) + ",";
            historyJson += "\"openBid\":" + DoubleToString(openBid, 5) + ",";
            historyJson += "\"closeAsk\":" + DoubleToString(closeAsk, 5) + ",";
            historyJson += "\"closeBid\":" + DoubleToString(closeBid, 5) + ",";
            historyJson += "\"openOrderSetupTimeMsc\":" + IntegerToString(openOrderSetupTimeMsc) + ",";
            historyJson += "\"closeOrderSetupTimeMsc\":" + IntegerToString(closeOrderSetupTimeMsc) + ",";
            historyJson += "\"openTicks\":\"" + EscapeJson(openTicksJson) + "\",";
            historyJson += "\"closeTicks\":\"" + EscapeJson(closeTicksJson) + "\"";
            historyJson += "}";
            
            // Progress logging every 100 closed trades
            if(!isIncremental && closedCount % 100 == 0)
            {
               int pct = (int)((double)i / (double)totalDeals * 100.0);
               if(cfg_DebugMode > 0)
                  Print("History progress: ", closedCount, " closed trades processed (", pct, "% of deals scanned)");
               UpdateStatusLabel("Reading History: " + IntegerToString(closedCount) + " trades (" + IntegerToString(pct) + "%)", clrWhite);
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
      g_nextRetryTime = TimeCurrent() + cfg_ReconnectIntervalSeconds;
      isRegistered = false;  // Will trigger re-registration with backoff
   }
}

//+------------------------------------------------------------------+
//| Send history update to server (incremental)                        |
//+------------------------------------------------------------------+
void SendHistoryUpdate()
{
   string url = cfg_ServerURL + "/api/history";

   // Guard: if sync time is missing but init was already marked as done, the
   // two persistence stores (GlobalVariable vs. file) are out of sync.
   // Doing a full-history scan here would use the 5-second timeout and loop
   // forever on large histories.  Force a clean re-init instead.
   if(g_lastSyncedCloseTime == "")
   {
      Print("Warning: g_lastSyncedCloseTime empty despite g_tradeListSent=true. Forcing re-init.");
      g_tradeListSent = false;
      GlobalVariableSet(GV_TRADELIST_SENT, 0);
      g_initRetryCount = 0;
      return;
   }

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
         if(cfg_DebugMode > 0)
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
      g_nextRetryTime = TimeCurrent() + cfg_ReconnectIntervalSeconds;
      isRegistered = false;  // Will trigger re-registration with backoff
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
   int postSize = ArraySize(postData);
   // Only strip a trailing null terminator if one is actually present
   // (matches the MQL4 client and avoids truncating a real last byte).
   if(postSize > 0 && postData[postSize - 1] == 0)
      ArrayResize(postData, postSize - 1);
   
   string headers = "Content-Type: application/json\r\n";
   if (StringLen(cfg_UserKey) > 0) {
       headers += "X-User-Key: " + cfg_UserKey + "\r\n";
   }
   
   ResetLastError();
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
         Print("Error 5200 (INVALID_ADDRESS): URL rejected by MT5. URL was: '", url, "'");
         Print("  Fix: In MT5 -> Tools -> Options -> Expert Advisors -> add EXACTLY: '", cfg_ServerURL, "'");
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
   
   string responseBody = CharArrayToString(result, 0, WHOLE_ARRAY, CP_UTF8);
   if(res == 200 || res == 201)
   {
      // Chatty log removed: successful heartbeat/trade update requests flood the logs
      // even in DebugMode.
      return true;
   }
   else
   {
      if(res != -1)
      {
         Print("HTTP request returned status: ", res, " URL: ", url);
         if(cfg_DebugMode > 0)
            Print("Server response: ", responseBody);
         
         if(res == 401 || res == 403)
          {
             Print("CRITICAL ERROR: API Key is invalid or unauthorized (HTTP ", res, "). Sync stopped.");
             g_authFailed = true;
          }
      }
      return false;
   }
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
   string result = "";
   int len = StringLen(text);
   for(int i = 0; i < len; i++)
   {
      ushort c = StringGetCharacter(text, i);
      if(c == '\\')      result += "\\\\";
      else if(c == '"')  result += "\\\"";
      else if(c == '\n') result += "\\n";
      else if(c == '\r') result += "\\r";
      else if(c == '\t') result += "\\t";
      else if(c == '\b') result += "\\b";
      else if(c == '\f') result += "\\f";
      else if(c < 32)
      {
         // Escape control chars < 32 as \u00xx
         string hex = "0123456789abcdef";
         ushort h1 = (c >> 12) & 0x0F;
         ushort h2 = (c >> 8) & 0x0F;
         ushort h3 = (c >> 4) & 0x0F;
         ushort h4 = c & 0x0F;
         result += "\\u" + StringSubstr(hex, h1, 1) + StringSubstr(hex, h2, 1) + StringSubstr(hex, h3, 1) + StringSubstr(hex, h4, 1);
      }
      else
      {
         result += ShortToString(c);
      }
   }
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
   
   // Check if the day rolled over. If so, flush the old file before resetting.
   if(GlobalVariableCheck(GV_LAST_LOG_DATE))
   {
      double lastDate = GlobalVariableGet(GV_LAST_LOG_DATE);
      if(lastDate > 0 && lastDate != currentDateNumeric)
      {
         // Flush remaining logs of the old day
         string oldLogDateStr = IntegerToString((long)lastDate);
         if(StringLen(oldLogDateStr) == 8)
         {
            string oldLogFileDate = StringSubstr(oldLogDateStr, 0, 4) + "." + StringSubstr(oldLogDateStr, 4, 2) + "." + StringSubstr(oldLogDateStr, 6, 2);
            string oldSrcPath = TerminalInfoString(TERMINAL_DATA_PATH) + "\\MQL5\\Logs\\" + oldLogFileDate + ".log";
            FlushRemainingLogs(oldSrcPath);
         }
         
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
   
   string srcPath = TerminalInfoString(TERMINAL_DATA_PATH) + "\\MQL5\\Logs\\" + logDate + ".log";
   string destFile = "ea_log_copy.txt";
   string destPath = TerminalInfoString(TERMINAL_DATA_PATH) + "\\MQL5\\Files\\" + destFile;
   
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
   if(SendLogLinesChunk(lines, 0, maxLines))
   {
      g_lastLogLinesSent += maxLines;
      GlobalVariableSet(GV_LAST_LOG_LINES, (double)g_lastLogLinesSent);
      if(cfg_DebugMode > 0)
         Print("EA logs sent: ", maxLines, " new lines (total tracked: ", g_lastLogLinesSent, ")");
   }
}

//+------------------------------------------------------------------+
//| Send a chunk of log lines to server                              |
//+------------------------------------------------------------------+
bool SendLogLinesChunk(string &lines[], int startIdx, int count)
{
   if(count <= 0) return true;
   
   string logsJson = "[";
   for(int i = 0; i < count; i++)
   {
      if(i > 0) logsJson += ",";
      logsJson += "\"" + EscapeJson(lines[startIdx + i]) + "\"";
   }
   logsJson += "]";
   
   string json = "{";
   json += "\"accountId\":" + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)) + ",";
   json += "\"logEntries\":" + logsJson + ",";
   json += "\"timestamp\":\"" + TimeToString(TimeCurrent(), TIME_DATE|TIME_SECONDS) + "\"";
   json += "}";
   
   string url = cfg_ServerURL + "/api/ea-logs";
   return SendHttpPost(url, json);
}

//+------------------------------------------------------------------+
//| Flush remaining logs of the old day before rollover             |
//+------------------------------------------------------------------+
void FlushRemainingLogs(string srcPath)
{
   string destFile = "ea_log_copy.txt";
   string destPath = TerminalInfoString(TERMINAL_DATA_PATH) + "\\MQL5\\Files\\" + destFile;
   
   if(!CopyFileW(srcPath, destPath, false))
   {
      Print("Warning: Could not copy old log file: ", srcPath);
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
   
   int totalNewLines = ArraySize(lines);
   int sentIdx = 0;
   while(sentIdx < totalNewLines)
   {
      int maxLines = MathMin(totalNewLines - sentIdx, 500);
      if(SendLogLinesChunk(lines, sentIdx, maxLines))
      {
         g_lastLogLinesSent += maxLines;
         sentIdx += maxLines;
      }
      else
      {
         break;
      }
   }
}
