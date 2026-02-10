//+------------------------------------------------------------------+
//|                                         TradeMonitorClient.mq5   |
//|                                    Trade Monitor Client EA       |
//|                        Sends trades to monitoring server         |
//+------------------------------------------------------------------+
#property copyright "TradeMonitor"
#property version   "1.00"
#property strict

//--- Input parameters
input string   ServerURL = "http://127.0.0.1:8080";   // Server URL (use IP, not localhost!)
input int      UpdateIntervalSeconds = 5;              // Update interval (seconds)
input int      HeartbeatIntervalSeconds = 30;          // Heartbeat interval (seconds)

//--- Global variables
datetime lastUpdateTime = 0;
datetime lastHeartbeatTime = 0;
bool isRegistered = false;
int httpTimeout = 5000;  // 5 seconds timeout

//+------------------------------------------------------------------+
//| Expert initialization function                                     |
//+------------------------------------------------------------------+
int OnInit()
{
   Print("TradeMonitorClient EA initialized");
   Print("Server URL: ", ServerURL);
   Print("Account ID: ", AccountInfoInteger(ACCOUNT_LOGIN));
   
   // Register with server on startup
   if(RegisterWithServer())
   {
      isRegistered = true;
      Print("Successfully registered with server");
   }
   else
   {
      Print("Failed to register with server - will retry");
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
   Print("TradeMonitorClient EA deinitialized");
}

//+------------------------------------------------------------------+
//| Timer function                                                     |
//+------------------------------------------------------------------+
void OnTimer()
{
   datetime currentTime = TimeCurrent();
   
   // Check if we need to register
   if(!isRegistered)
   {
      if(RegisterWithServer())
      {
         isRegistered = true;
         Print("Successfully registered with server");
      }
      return;
   }
   
   // Send trade updates
   if(currentTime - lastUpdateTime >= UpdateIntervalSeconds)
   {
      SendTradeUpdate();
      SendHistoryUpdate();  // Also send history with each update
      lastUpdateTime = currentTime;
   }
   
   // Send heartbeat
   if(currentTime - lastHeartbeatTime >= HeartbeatIntervalSeconds)
   {
      SendHeartbeat();
      lastHeartbeatTime = currentTime;
   }
}

//+------------------------------------------------------------------+
//| Register with monitoring server                                    |
//+------------------------------------------------------------------+
bool RegisterWithServer()
{
   string url = ServerURL + "/api/register";
   
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
//| Send trade update to server                                        |
//+------------------------------------------------------------------+
void SendTradeUpdate()
{
   string url = ServerURL + "/api/trades";
   
   // Build trades array
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
//| Send history update to server                                      |
//+------------------------------------------------------------------+
void SendHistoryUpdate()
{
   string url = ServerURL + "/api/history";
   
   // Select history for the last 30 days
   datetime fromDate = TimeCurrent() - 30 * 24 * 60 * 60;
   datetime toDate = TimeCurrent();
   
   if(!HistorySelect(fromDate, toDate))
   {
      Print("Failed to select history");
      return;
   }
   
   // Build closed trades array
   string historyJson = "[";
   int totalDeals = HistoryDealsTotal();
   bool firstDeal = true;
   
   for(int i = 0; i < totalDeals; i++)
   {
      ulong ticket = HistoryDealGetTicket(i);
      if(ticket > 0)
      {
         // Only include deals that close positions (DEAL_ENTRY_OUT)
         ENUM_DEAL_ENTRY entry = (ENUM_DEAL_ENTRY)HistoryDealGetInteger(ticket, DEAL_ENTRY);
         if(entry == DEAL_ENTRY_OUT)
         {
            if(!firstDeal) historyJson += ",";
            firstDeal = false;
            
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
            historyJson += "\"closeTime\":\"" + TimeToString((datetime)HistoryDealGetInteger(ticket, DEAL_TIME), TIME_DATE|TIME_SECONDS) + "\",";
            historyJson += "\"profit\":" + DoubleToString(HistoryDealGetDouble(ticket, DEAL_PROFIT), 2) + ",";
            historyJson += "\"swap\":" + DoubleToString(HistoryDealGetDouble(ticket, DEAL_SWAP), 2) + ",";
            historyJson += "\"commission\":" + DoubleToString(HistoryDealGetDouble(ticket, DEAL_COMMISSION), 2) + ",";
            historyJson += "\"magicNumber\":" + IntegerToString(HistoryDealGetInteger(ticket, DEAL_MAGIC)) + ",";
            historyJson += "\"comment\":\"" + EscapeJson(HistoryDealGetString(ticket, DEAL_COMMENT)) + "\"";
            historyJson += "}";
         }
      }
   }
   historyJson += "]";
   
   // Build main JSON payload
   string json = "{";
   json += "\"accountId\":" + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)) + ",";
   json += "\"closedTrades\":" + historyJson + ",";
   json += "\"timestamp\":\"" + TimeToString(TimeCurrent(), TIME_DATE|TIME_SECONDS) + "\"";
   json += "}";
   
   SendHttpPost(url, json);  // Don't fail registration on history error
}

//+------------------------------------------------------------------+
//| Send heartbeat to server                                           |
//+------------------------------------------------------------------+
void SendHeartbeat()
{
   string url = ServerURL + "/api/heartbeat";
   
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
   char postData[];
   char result[];
   string resultHeaders;
   
   // Convert string to char array
   StringToCharArray(json, postData, 0, WHOLE_ARRAY, CP_UTF8);
   ArrayResize(postData, ArraySize(postData) - 1);  // Remove null terminator
   
   string headers = "Content-Type: application/json\r\n";
   
   ResetLastError();
   int res = WebRequest("POST", url, headers, httpTimeout, postData, result, resultHeaders);
   
   if(res == -1)
   {
      int error = GetLastError();
      if(error == 4060)
      {
         Print("Error: URL not allowed. Please add ", ServerURL, " to allowed URLs in Tools -> Options -> Expert Advisors");
      }
      else
      {
         Print("HTTP request failed, error: ", error);
      }
      return false;
   }
   
   if(res != 200)
   {
      Print("HTTP request returned status: ", res);
      return false;
   }
   
   return true;
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
