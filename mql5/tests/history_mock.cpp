// Runtime shim only: run-history-tests.cjs inserts the current production function.
#include <algorithm>
#include <cstdint>
#include <ctime>
#include <iomanip>
#include <iostream>
#include <map>
#include <sstream>
#include <string>
#include <vector>
using std::string;
using datetime = int64_t;
using ENUM_DEAL_ENTRY = int;
using ENUM_DEAL_TYPE = int;
enum { DEAL_ENTRY_IN, DEAL_ENTRY_OUT, DEAL_ENTRY_INOUT, DEAL_ENTRY_OUT_BY };
enum { DEAL_TYPE_BUY, DEAL_TYPE_SELL };
enum { DEAL_ENTRY, DEAL_TIME, DEAL_TIME_MSC, DEAL_MAGIC, DEAL_POSITION_ID,
       DEAL_TYPE, DEAL_ORDER, DEAL_SYMBOL, DEAL_COMMENT, DEAL_PRICE,
       DEAL_COMMISSION, DEAL_VOLUME, DEAL_PROFIT, DEAL_SWAP };
enum { TIME_DATE = 1, TIME_SECONDS = 2, PERIOD_M5, PERIOD_M15, PERIOD_H1, clrWhite };
int cfg_DebugMode = 0;
std::map<string, double> globalVariables;
string GV_HISTORY_REVISION = "TM_HistoryRevision_123", GV_TRADELIST_SENT = "TM_Trade_Sent_123";
bool g_tradeListSent = true;
datetime g_tradeListSentTime = 123;
string g_lastSyncedCloseTime = "2026.09.14 16:11:41", savedSyncTime = g_lastSyncedCloseTime;
int saveCalls = 0, postCalls = 0;
bool postSuccess = true;
string postedJson;
bool GlobalVariableCheck(string key) { return globalVariables.count(key) != 0; }
double GlobalVariableGet(string key) { return globalVariables[key]; }
void GlobalVariableSet(string key, double value) { globalVariables[key] = value; }
void SaveLastSyncTime(string value) { savedSyncTime = value; ++saveCalls; }
string cfg_ServerURL = "https://example.invalid";
int64_t g_assignedAccountId = 0;
int httpTimeoutInit = 60000;
enum { ACCOUNT_LOGIN, ACCOUNT_EQUITY, ACCOUNT_BALANCE };
int64_t AccountInfoInteger(int) { return 123; }
double AccountInfoDouble(int) { return 1000; }
string BuildOpenTradesJson() { return "[]"; }
bool SendHttpPostWithTimeout(string, string json, int) {
  ++postCalls; postedJson = json; return postSuccess;
}
struct MqlDateTime { int year, mon, day, hour, min, sec, day_of_week; };
datetime StringToTime(string value) {
  std::tm t{}; std::istringstream in(value);
  in >> std::get_time(&t, "%Y.%m.%d %H:%M:%S");
#ifdef _WIN32
  return _mkgmtime(&t);
#else
  return timegm(&t);
#endif
}
string TimeToString(datetime value, int) {
  std::time_t t = value; auto tm = *std::gmtime(&t);
  std::ostringstream out; out << std::put_time(&tm, "%Y.%m.%d %H:%M:%S"); return out.str();
}
void TimeToStruct(datetime value, MqlDateTime &out) {
  std::time_t t = value; auto tm = *std::gmtime(&t);
  out = {tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday, tm.tm_hour, tm.tm_min, tm.tm_sec, tm.tm_wday};
}
datetime StructToTime(MqlDateTime t) {
  std::ostringstream out;
  out << t.year << '.' << t.mon << '.' << t.day << ' ' << t.hour << ':' << t.min << ':' << t.sec;
  return StringToTime(out.str());
}
datetime TimeCurrent() { return StringToTime("2026.09.15 12:00:00"); }
int StringLen(string s) { return static_cast<int>(s.size()); }
string IntegerToString(int64_t n) { return std::to_string(n); }
string DoubleToString(double n, int digits) {
  std::ostringstream out; out << std::fixed << std::setprecision(digits) << n; return out.str();
}
string EscapeJson(string s) {
  string out;
  for (char c : s) { if (c == '"' || c == '\\') out += '\\'; out += c; }
  return out;
}
template<class... T> void Print(T... args) { (std::cerr << ... << args) << '\n'; }
void UpdateStatusLabel(string, int) {}
void GetMarketBidAsk(string, int64_t, double &bid, double &ask) { bid = 0; ask = 0; }
string GetTicksJson(string, int64_t) { return "[]"; }
string GetRatesAsJson(string, int, datetime, datetime) { return "[]"; }
int64_t GetOrderSetupTimeMsc(uint64_t) { return 0; }
struct Deal {
  uint64_t ticket; int64_t position; int entry, type; datetime time;
  double price, volume, commission; int64_t magic; string comment;
};
std::vector<Deal> deals;
std::vector<uint64_t> selected;
int selectCalls = 0, positionCalls = 0;
bool failInitial = false, failLookup = false, failRestore = false;
bool HistorySelect(datetime from, datetime to) {
  ++selectCalls; selected.clear();
  if (failInitial || (failRestore && selectCalls > 1)) return false;
  for (const auto &d : deals) if (d.time >= from && d.time <= to) selected.push_back(d.ticket);
  return true;
}
bool HistorySelectByPosition(uint64_t position) {
  ++positionCalls; selected.clear();
  if (failLookup) return false;
  for (const auto &d : deals) if (d.position == static_cast<int64_t>(position)) selected.push_back(d.ticket);
  return true;
}
int HistoryDealsTotal() { return static_cast<int>(selected.size()); }
uint64_t HistoryDealGetTicket(int index) { return index >= 0 && index < HistoryDealsTotal() ? selected[index] : 0; }
const Deal *findDeal(uint64_t ticket) {
  // Deliberately require selected membership: properties disappear after a failed lookup
  // until the original range is restored, which catches misuse of history selection.
  if (std::find(selected.begin(), selected.end(), ticket) == selected.end()) return nullptr;
  for (const auto &d : deals) if (d.ticket == ticket) return &d;
  return nullptr;
}
int64_t HistoryDealGetInteger(uint64_t ticket, int prop) {
  auto d = findDeal(ticket); if (!d) return 0;
  switch (prop) {
    case DEAL_ENTRY: return d->entry; case DEAL_TIME: return d->time;
    case DEAL_TIME_MSC: return d->time * 1000 + 123; case DEAL_MAGIC: return d->magic;
    case DEAL_POSITION_ID: return d->position; case DEAL_TYPE: return d->type;
    case DEAL_ORDER: return d->ticket + 10000; default: return 0;
  }
}
double HistoryDealGetDouble(uint64_t ticket, int prop) {
  auto d = findDeal(ticket); if (!d) return 0;
  switch (prop) {
    case DEAL_PRICE: return d->price; case DEAL_VOLUME: return d->volume;
    case DEAL_COMMISSION: return d->commission; case DEAL_PROFIT: return 10;
    default: return 0;
  }
}
string HistoryDealGetString(uint64_t ticket, int prop) {
  auto d = findDeal(ticket); if (!d) return "";
  return prop == DEAL_SYMBOL ? "XAUUSD" : d->comment;
}

// PRODUCTION_FUNCTION

void add(uint64_t ticket, int64_t position, int entry, int type, string time,
         double price, double volume, double commission, int64_t magic = 0, string comment = "[sl]") {
  deals.push_back({ticket, position, entry, type, StringToTime("2026.09.14 " + time), price, volume, commission, magic, comment});
}
int main(int argc, char **argv) {
  string scenario = argc > 1 ? argv[1] : "sell";
#ifdef HAS_REVISION_TESTS
  if (scenario.rfind("revision-", 0) == 0 || scenario.rfind("upload-", 0) == 0) {
    globalVariables[GV_TRADELIST_SENT] = 123;
    if (scenario == "revision-older") globalVariables[GV_HISTORY_REVISION] = HISTORY_SYNC_REVISION - 1;
    if (scenario == "revision-current") globalVariables[GV_HISTORY_REVISION] = HISTORY_SYNC_REVISION;
    if (scenario == "revision-newer") globalVariables[GV_HISTORY_REVISION] = HISTORY_SYNC_REVISION + 1;
    bool success = false;
    PrepareHistoryRevision();
    if (scenario.rfind("upload-", 0) == 0) {
      failInitial = scenario == "upload-selection-failure";
      postSuccess = scenario != "upload-post-failure";
      if (scenario == "upload-with-trades") {
        add(101, 1, DEAL_ENTRY_IN, DEAL_TYPE_SELL, "12:25:00", 4284.26, .05, -.13);
        add(201, 1, DEAL_ENTRY_OUT, DEAL_TYPE_BUY, "16:47:45", 4278.87, .05, -.13);
      }
      success = SendInitialTradeList();
    }
    std::cout << "{\"success\":" << (success ? "true" : "false")
      << ",\"tradeListSent\":" << (g_tradeListSent ? "true" : "false")
      << ",\"tradeListTime\":" << g_tradeListSentTime
      << ",\"tradeListMarker\":" << globalVariables[GV_TRADELIST_SENT]
      << ",\"bookmark\":\"" << g_lastSyncedCloseTime
      << "\",\"savedBookmark\":\"" << savedSyncTime
      << "\",\"saveCalls\":" << saveCalls << ",\"postCalls\":" << postCalls
      << ",\"revision\":" << (GlobalVariableCheck(GV_HISTORY_REVISION) ? IntegerToString(GlobalVariableGet(GV_HISTORY_REVISION)) : "null")
      << ",\"expectedRevision\":" << HISTORY_SYNC_REVISION
      << ",\"posted\":" << (postedJson.empty() ? "null" : postedJson) << "}\n\n0 0\n";
    return 0;
  }
#endif
  bool buy = scenario == "buy";
  int entryType = buy ? DEAL_TYPE_BUY : DEAL_TYPE_SELL;
  int exitType = buy ? DEAL_TYPE_SELL : DEAL_TYPE_BUY;
  if (scenario == "partial") {
    add(101, 1, DEAL_ENTRY_IN, entryType, "12:26:00", 4285.30, .17, -.44, 1234567890123LL, "Gold Spike MT5");
    add(201, 1, DEAL_ENTRY_OUT, exitType, "16:47:45", 4278.87, .05, -.13);
    add(202, 1, DEAL_ENTRY_OUT, exitType, "16:47:47", 4280.31, .12, -.31);
  } else {
    if (scenario != "missing") {
      add(101, 1, DEAL_ENTRY_IN, entryType, "12:25:00", 4284.26, .05, -.13, 1234567890123LL, "Gold Spike MT5");
      add(102, 2, DEAL_ENTRY_IN, entryType, "12:26:00", 4282.75, .17, -.44, 9876543210123LL, "Gold Spike MT5");
    }
    add(249828460, 1, DEAL_ENTRY_OUT, exitType, "16:47:45", 4278.87, .05, -.13);
    add(249828953, 2, DEAL_ENTRY_OUT, exitType, "16:47:47", 4280.31, .17, -.44);
  }
  failLookup = scenario == "lookup-failure";
  failRestore = scenario == "restore-failure";
  failInitial = scenario == "initial-failure";
  string latest = "";
  string result = BuildClosedTradesJson(scenario == "full" ? "" : "2026.09.14 16:11:41", latest);
  std::cout << result << '\n' << latest << '\n' << selectCalls << ' ' << positionCalls << '\n';
}
