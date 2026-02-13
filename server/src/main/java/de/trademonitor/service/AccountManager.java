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
}
