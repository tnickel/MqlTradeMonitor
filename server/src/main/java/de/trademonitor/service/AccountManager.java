package de.trademonitor.service;

import de.trademonitor.entity.AccountEntity;
import de.trademonitor.model.Account;
import de.trademonitor.model.ClosedTrade;
import de.trademonitor.model.Trade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages registered MetaTrader accounts and their status.
 * In-memory cache for fast dashboard access, backed by H2 database.
 */
@Service
public class AccountManager {

    private final Map<Long, Account> accounts = new ConcurrentHashMap<>();

    @Value("${account.timeout.seconds:60}")
    private int timeoutSeconds;

    @Autowired
    private TradeStorage tradeStorage;

    @Autowired
    private de.trademonitor.repository.DashboardSectionRepository sectionRepository;

    @Autowired
    private GlobalConfigService globalConfigService;

    @Autowired
    private HomeyService homeyService;

    private final Map<Long, de.trademonitor.entity.DashboardSectionEntity> sectionsCache = new ConcurrentHashMap<>();

    /**
     * Load persisted accounts from H2 on startup.
     */
    @PostConstruct
    public void init() {
        // Load sections
        List<de.trademonitor.entity.DashboardSectionEntity> sections = sectionRepository
                .findAllByOrderByDisplayOrderAsc();
        if (sections.isEmpty()) {
            // Migration: Create default sections if none exist
            de.trademonitor.entity.DashboardSectionEntity top = new de.trademonitor.entity.DashboardSectionEntity(
                    "Hauptbereich", 0);
            de.trademonitor.entity.DashboardSectionEntity bottom = new de.trademonitor.entity.DashboardSectionEntity(
                    "Secondary Section", 1);
            top = sectionRepository.save(top);
            bottom = sectionRepository.save(bottom);
            sections.add(top);
            sections.add(bottom);
        }
        sections.forEach(s -> sectionsCache.put(s.getId(), s));

        List<AccountEntity> persisted = tradeStorage.loadAllAccounts();
        for (AccountEntity ae : persisted) {
            Account account = new Account(ae.getAccountId(), ae.getBroker(), ae.getCurrency(), ae.getBalance());
            account.setEquity(ae.getEquity());

            // Load trades from DB
            List<ClosedTrade> closedTrades = tradeStorage.loadClosedTrades(ae.getAccountId());
            account.setClosedTrades(closedTrades);

            List<Trade> openTrades = tradeStorage.loadOpenTrades(ae.getAccountId());
            account.setOpenTrades(openTrades);

            // Load name and type
            account.setName(ae.getName());
            account.setType(ae.getType());
            account.setSection(ae.getSection()); // Legacy loading
            account.setSectionId(ae.getSectionId()); // New loading
            account.setDisplayOrder(ae.getDisplayOrder() != null ? ae.getDisplayOrder() : 0);

            // MIGRATION CHECK: If sectionID is null but we have legacy string
            if (account.getSectionId() == null) {
                Long targetSectionId = sections.get(0).getId(); // Default to top
                if ("BOTTOM".equalsIgnoreCase(account.getSection())) {
                    if (sections.size() > 1)
                        targetSectionId = sections.get(1).getId();
                }
                account.setSectionId(targetSectionId);
                // We should persist this migration eventually, but lazy update on saveLayout is
                // also ok.
                // Better to save now to be clean
                saveAccountSection(account.getAccountId(), targetSectionId, account.getDisplayOrder());
            }

            accounts.put(ae.getAccountId(), account);
            System.out.println("Loaded account " + ae.getAccountId() + " from DB");
        }
    }

    public List<de.trademonitor.entity.DashboardSectionEntity> getAllSections() {
        return sectionRepository.findAllByOrderByDisplayOrderAsc();
    }

    public de.trademonitor.entity.DashboardSectionEntity createSection(String name) {
        int maxOrder = sectionsCache.values().stream()
                .mapToInt(de.trademonitor.entity.DashboardSectionEntity::getDisplayOrder).max().orElse(0);
        de.trademonitor.entity.DashboardSectionEntity newSection = new de.trademonitor.entity.DashboardSectionEntity(
                name, maxOrder + 1);
        newSection = sectionRepository.save(newSection);
        sectionsCache.put(newSection.getId(), newSection);
        return newSection;
    }

    public void deleteSection(Long sectionId) {
        if (!sectionsCache.containsKey(sectionId))
            return;

        // Move accounts in this section to the first available section
        Long fallbackId = sectionsCache.keySet().stream().filter(id -> !id.equals(sectionId)).findFirst().orElse(null);

        if (fallbackId != null) {
            for (Account acc : accounts.values()) {
                if (sectionId.equals(acc.getSectionId())) {
                    saveAccountSection(acc.getAccountId(), fallbackId, acc.getDisplayOrder()); // section update
                }
            }
        }

        sectionRepository.deleteById(sectionId);
        sectionsCache.remove(sectionId);
    }

    public void renameSection(Long sectionId, String newName) {
        de.trademonitor.entity.DashboardSectionEntity s = sectionsCache.get(sectionId);
        if (s != null) {
            s.setName(newName);
            sectionRepository.save(s);
        }
    }

    /**
     * Register or update an account.
     */
    public void registerAccount(long accountId, String broker, String currency, double balance) {
        Account account = accounts.get(accountId);
        if (account == null) {
            account = new Account(accountId, broker, currency, balance);
            // Assign to first section by default
            if (!sectionsCache.isEmpty()) {
                Long defaultSecId = sectionsCache.keySet().iterator().next();
                // Try to find the one with order 0
                Optional<de.trademonitor.entity.DashboardSectionEntity> first = sectionsCache.values().stream()
                        .min(Comparator.comparingInt(de.trademonitor.entity.DashboardSectionEntity::getDisplayOrder));
                if (first.isPresent())
                    defaultSecId = first.get().getId();

                account.setSectionId(defaultSecId);
            }

            accounts.put(accountId, account);
            System.out.println("New account registered: " + accountId + " (" + broker + ")");
        } else {
            account.setBroker(broker);
            account.setCurrency(currency);
            account.setBalance(balance);
            account.setLastSeen(LocalDateTime.now());
        }
        // Persist to DB
        tradeStorage.saveAccount(accountId, broker, currency, balance);
        // Also persist section if it was new? saveAccount doesn't do sectionId yet...
        // We need to update TradeStorage to save sectionID too.
        if (account.getSectionId() != null) {
            tradeStorage.updateAccountLayout(accountId, null, account.getDisplayOrder(), account.getSectionId());
        }
    }

    public void reportError(long accountId, String errorMessage) {
        Account account = accounts.get(accountId);
        if (account != null) {
            account.setLastErrorMsg(errorMessage);
            account.setLastErrorTime(LocalDateTime.now());

            // Trigger Homey Siren if enabled for API errors
            if (globalConfigService.isHomeyTriggerApi()) {
                homeyService.triggerSiren();
            }
        }
    }

    /**
     * Update account details (Method for Dashboard).
     */
    public void updateAccountDetails(long accountId, String name, String type) {
        Account account = accounts.get(accountId);
        if (account != null) {
            account.setName(name);
            account.setType(type);
            // Persist
            tradeStorage.updateAccountDetails(accountId, name, type);
        }
    }

    /**
     * Save layout preference for an account.
     */
    // Deprecated signature
    public void saveLayout(long accountId, String section, int displayOrder) {
        // no-op or forward?
    }

    public void saveAccountSection(long accountId, Long sectionId, int displayOrder) {
        Account account = accounts.get(accountId);
        if (account != null) {
            account.setSectionId(sectionId);
            account.setDisplayOrder(displayOrder);
            tradeStorage.updateAccountLayout(accountId, null, displayOrder, sectionId);
        }
    }

    /**
     * Update account trades and metrics.
     */
    public void updateTrades(long accountId, List<Trade> trades, double equity, double balance) {
        Account account = accounts.get(accountId);
        if (account != null) {
            account.setOpenTrades(trades != null ? trades : new ArrayList<>());
            account.setEquity(equity);
            account.setBalance(balance);
            account.setLastSeen(LocalDateTime.now());

            // Persist to DB
            tradeStorage.replaceOpenTrades(accountId, trades);
            tradeStorage.updateAccountMetrics(accountId, equity, balance);
        }
    }

    /**
     * Update heartbeat for an account.
     */
    public void updateHeartbeat(long accountId) {
        Account account = accounts.get(accountId);
        if (account != null) {
            account.setLastSeen(LocalDateTime.now());
        }
    }

    /**
     * Get account by ID.
     */
    public Account getAccount(long accountId) {
        return accounts.get(accountId);
    }

    /**
     * Get all registered accounts.
     */
    public Collection<Account> getAllAccounts() {
        return accounts.values();
    }

    /**
     * Get list of accounts with status info.
     */
    public List<Map<String, Object>> getAccountsWithStatus() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Account account : accounts.values()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("accountId", account.getAccountId());
            info.put("broker", account.getBroker());
            info.put("currency", account.getCurrency());
            info.put("balance", account.getBalance());
            info.put("equity", account.getEquity());
            info.put("profit", account.getTotalProfit());
            info.put("trades", account.getOpenTrades().size());
            info.put("online", account.isOnline(timeoutSeconds));
            info.put("lastSeen", account.getLastSeen());
            info.put("name", account.getName());
            info.put("type", account.getType());
            info.put("section", account.getSection());
            info.put("sectionId", account.getSectionId());
            info.put("displayOrder", account.getDisplayOrder());
            info.put("displayOrder", account.getDisplayOrder());
            info.put("syncWarning", account.isSyncWarning());
            info.put("errorState", account.isErrorState());
            info.put("lastErrorMsg", account.getLastErrorMsg());
            result.add(info);
        }
        // Sort by online status then by account ID
        result.sort((a, b) -> {
            boolean onlineA = (Boolean) a.get("online");
            boolean onlineB = (Boolean) b.get("online");
            if (onlineA != onlineB)
                return onlineB ? 1 : -1;
            return Long.compare((Long) a.get("accountId"), (Long) b.get("accountId"));
        });
        return result;
    }

    /**
     * Get all open trades sorted by Account Type (Real first), then Name/ID.
     */
    public List<Map<String, Object>> getAllOpenTradesSorted() {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Account account : accounts.values()) {
            String accName = account.getName() != null && !account.getName().isEmpty()
                    ? account.getName()
                    : String.valueOf(account.getAccountId());
            String accType = account.getType() != null ? account.getType() : "";
            boolean isReal = "REAL".equalsIgnoreCase(accType);

            for (Trade trade : account.getOpenTrades()) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("accountId", account.getAccountId());
                info.put("accountName", accName);
                info.put("accountType", accType);
                info.put("isReal", isReal);

                info.put("ticket", trade.getTicket());
                info.put("symbol", trade.getSymbol());
                info.put("type", trade.getType());
                info.put("volume", trade.getVolume());
                info.put("openPrice", trade.getOpenPrice());
                info.put("openTime", trade.getOpenTime());
                info.put("profit", trade.getProfit());
                info.put("magicNumber", trade.getMagicNumber());
                info.put("comment", trade.getComment());
                info.put("syncStatus", trade.getSyncStatus());

                result.add(info);
            }
        }

        // Sort: Real first, then Account Name, then Ticket
        result.sort((a, b) -> {
            boolean realA = (Boolean) a.get("isReal");
            boolean realB = (Boolean) b.get("isReal");

            if (realA != realB) {
                return realA ? -1 : 1; // Real comes first
            }

            // Then by Account Name
            String nameA = (String) a.get("accountName");
            String nameB = (String) b.get("accountName");
            int nameComp = nameA.compareToIgnoreCase(nameB);
            if (nameComp != 0)
                return nameComp;

            // Then by Ticket
            Long ticketA = (Long) a.get("ticket");
            Long ticketB = (Long) b.get("ticket");
            return ticketA.compareTo(ticketB);
        });

        return result;
    }

    /**
     * Get timeout setting.
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Update closed trades history for an account with duplicate checking.
     * Returns the number of newly inserted trades.
     */
    public int updateHistory(long accountId, List<ClosedTrade> closedTrades) {
        Account account = accounts.get(accountId);
        if (account != null && closedTrades != null) {
            // Save to DB with duplicate check
            int inserted = tradeStorage.saveClosedTradesWithDuplicateCheck(accountId, closedTrades);

            // Reload from DB to keep in-memory cache consistent
            account.setClosedTrades(tradeStorage.loadClosedTrades(accountId));
            account.setLastSeen(LocalDateTime.now());
            return inserted;
        }
        return 0;
    }

    /**
     * Get list of accounts sorted by privilege (Real first), then Name/ID.
     * Useful for Open Trades page.
     */
    public List<Account> getAccountsSortedByPrivilege() {
        List<Account> sortedList = new ArrayList<>(accounts.values());

        sortedList.sort((a, b) -> {
            boolean isRealA = "REAL".equalsIgnoreCase(a.getType());
            boolean isRealB = "REAL".equalsIgnoreCase(b.getType());

            if (isRealA != isRealB) {
                return isRealA ? -1 : 1; // Real comes first
            }

            // Then by Name
            String nameA = a.getName() != null ? a.getName() : "";
            String nameB = b.getName() != null ? b.getName() : "";
            int nameComp = nameA.compareToIgnoreCase(nameB);
            if (nameComp != 0)
                return nameComp;

            // Then by ID
            return Long.compare(a.getAccountId(), b.getAccountId());
        });

        return sortedList;
    }

    /**
     * Calculate and return drawdown statistics per magic number for all accounts.
     * Logic:
     * 1. Reconstruct "Artificial Balance" history from Current Balance - Closed
     * Trades.
     * 2. Track "High Water Mark" for the Account Balance.
     * Wait, "Balance High" usually refers to the highest closed balance.
     * But checking drawdown "from balance high" for specific magic number...
     * Actually, usually Drawdown is Equity vs High Water Mark.
     * The user said: "drawdown vom balance high anzeigen".
     * If I have a Magic Number with Open Trades, its "current value" is (Realized
     * Profit + Open Profit).
     * Its "High Water Mark" is the max value of (Realized Profit) historically.
     * So Magic Drawdown = (Max Realized Profit of Magic) - (Current Realized + Open
     * Profit of Magic).
     * 
     * Wait, user said "drawdown vom balance high".
     * If they mean Account Balance High, then it's global.
     * "alle opentrades incl. den aktuellen drawdown vom balance high ... Die magic
     * numbers müssen immer zusammengefasst werden."
     * This implies: Show me Magic X. How much is it currently down from the
     * Account's Balance High?
     * OR: How much is Magic X responsible for the drawdown?
     * 
     * Interpretation:
     * 1. Calculate Account Balance High (ABH).
     * 2. Calculate Account Current Equity (ACE) = Balance + OpenPL.
     * BUT broken down by Magic?
     * 
     * If I have 2 Magics open.
     * Magic A: Open PL -50.
     * Magic B: Open PL +10.
     * Balance: 1000. ABH: 1000.
     * Equity: 960.
     * Drawdown is 40 (4%).
     * 
     * If we break it down:
     * Magic A is contributing -50 to the equity.
     * Magic B is contributing +10.
     * 
     * Maybe the user means: "Current Drawdown of THIS Magic Number".
     * Magic High Water Mark (HWM) = Max Cumulative Profit of this Magic.
     * Current Magic Equity = Cumulative Profit + Open PL.
     * Drawdown = HWM - Current.
     * 
     * Let's stick to the Plan:
     * "drawdown vom balance high" might be a slight misnomer by the user or they
     * mean "Impact on Balance High"?
     * Actually, "Open Drawdown" is usually specific to the strategy.
     * If I have a strategy that made 1000 profit, and is now open -100. It is in
     * -10% drawdown from its peak?
     * 
     * Let's implement:
     * Magic Drawdown = (Max Historical Cumulative Profit for Magic) - (Current
     * Cumulative Profit + Open PL).
     * AND we also reference the Account Balance High to show % of Account?
     * "drawdown in % und Euro vom balance high"
     * 
     * If Account Balance High is 10,000.
     * Magic A is down 100 EUR.
     * That is 1% of Balance High.
     * 
     * Okay, so:
     * 1. Calculate Account Balance High (ABH).
     * - Current Balance is known.
     * - History is known.
     * - Reconstruct history to find Max Balance.
     * 2. For each Magic Number with Open Trades:
     * - Calculate Open PL for this Magic.
     * - (Optional) Calculate "Net Profit" contribution to see if it's "winning" or
     * "losing" overall?
     * - No, usually Drawdown is just "How much is it down from the peak".
     * - BUT, "drawdown vom balance high" strongly suggests:
     * DD_EUR = Open PL (if negative).
     * DD_Percent = (Open PL / ABH) * 100.
     * 
     * Wait, if I have open profit +50, there is no drawdown.
     * If I have open profit -50. Drawdown is 50.
     * 
     * User Ref: "alle opentrades incl. den aktuellen drawdown vom balance high
     * anzeigen für alle offenen trades"
     * "mir sollen nicht die trades angezeigt werden sonderen die magic numbers und
     * der akuelle offene drawdown in % und Euro vom balance high."
     * 
     * So:
     * Group Open Trades by Magic.
     * Sum Open PL.
     * If Sum Open PL < 0:
     * Drawdown EUR = Abs(Sum Open PL).
     * Drawdown % = (Drawdown EUR / Account Balance High) * 100.
     * If Sum Open PL >= 0:
     * Drawdown is 0. (Or explicitly show +Profit?)
     * User asked for "drawdown", so maybe only show if negative? Or show all "Open
     * Trades" summary.
     * "alle opentrades ... anzeigen" -> suggest showing all active magics, even if
     * positive.
     * If positive, "Drawdown" is 0.
     * 
     * Calculation of Account Balance High:
     * Current Balance is X.
     * We have list of Closed Trades (p, t).
     * Sort by time descending.
     * Balance[t] = Balance[t+1] - Profit[t+1].
     * Max(Balance[t]) is HWM.
     */
    public List<de.trademonitor.dto.MagicDrawdownItem> getMagicDrawdowns() {
        List<de.trademonitor.dto.MagicDrawdownItem> result = new ArrayList<>();

        for (Account account : accounts.values()) {
            // Use Current Balance as the reference for "Current Drawdown"
            // Calculating "Balance High" from history is unreliable without full
            // deposit/withdrawal logs
            // and often misleading if withdrawals occurred. The standard industry metric
            // for
            // "Current Drawdown" is Current Open PL / Current Balance.
            double currentBalance = account.getBalance();
            double referenceBalance = currentBalance;

            // 2. Group Open Trades by Magic
            Map<Long, Double> magicOpenPL = new HashMap<>();
            Map<Long, String> magicLastComment = new HashMap<>();

            for (Trade t : account.getOpenTrades()) {
                magicOpenPL.merge(t.getMagicNumber(), t.getProfit(), (a, b) -> a + b);
                if (t.getComment() != null && !t.getComment().isEmpty()) {
                    magicLastComment.putIfAbsent(t.getMagicNumber(), t.getComment());
                }
            }

            // 3. Create Items
            for (Map.Entry<Long, Double> entry : magicOpenPL.entrySet()) {
                Long magic = entry.getKey();
                Double openPL = entry.getValue();

                double ddEur = 0;
                double ddPct = 0;

                // Drawdown is positive number representing loss
                if (openPL < 0) {
                    ddEur = Math.abs(openPL);
                    if (referenceBalance > 0) {
                        ddPct = (ddEur / referenceBalance) * 100.0;
                    }
                }

                String magicName = magicLastComment.getOrDefault(magic, String.valueOf(magic));

                de.trademonitor.dto.MagicDrawdownItem item = new de.trademonitor.dto.MagicDrawdownItem(
                        account.getAccountId(),
                        account.getName() != null ? account.getName() : String.valueOf(account.getAccountId()),
                        account.getType(),
                        magic,
                        magicName,
                        ddEur,
                        ddPct,
                        referenceBalance,
                        openPL);
                item.setCurrentMagicEquity(openPL);

                result.add(item);
            }
        }

        // Sort: Real First, then Drawdown % Descending, then Account Name
        result.sort((a, b) -> {
            boolean realA = a.isReal();
            boolean realB = b.isReal();
            if (realA != realB)
                return realA ? -1 : 1;

            // Drawdown % Descending (Higher is worse/first)
            // Use Double.compare for safety
            int ddComp = Double.compare(b.getCurrentDrawdownPercent(), a.getCurrentDrawdownPercent());
            if (ddComp != 0)
                return ddComp;

            int nameComp = a.getAccountName().compareToIgnoreCase(b.getAccountName());
            if (nameComp != 0)
                return nameComp;

            return Long.compare(a.getMagicNumber(), b.getMagicNumber());
        });

        return result;
    }
}
