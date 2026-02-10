package de.trademonitor.service;

import de.trademonitor.model.Account;
import de.trademonitor.model.ClosedTrade;
import de.trademonitor.model.Trade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages registered MetaTrader accounts and their status.
 */
@Service
public class AccountManager {

    private final Map<Long, Account> accounts = new ConcurrentHashMap<>();

    @Value("${account.timeout.seconds:60}")
    private int timeoutSeconds;

    /**
     * Register or update an account.
     */
    public void registerAccount(long accountId, String broker, String currency, double balance) {
        Account account = accounts.get(accountId);
        if (account == null) {
            account = new Account(accountId, broker, currency, balance);
            accounts.put(accountId, account);
            System.out.println("New account registered: " + accountId + " (" + broker + ")");
        } else {
            account.setBroker(broker);
            account.setCurrency(currency);
            account.setBalance(balance);
            account.setLastSeen(LocalDateTime.now());
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
     * Get timeout setting.
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Update closed trades history for an account.
     */
    public void updateHistory(long accountId, List<ClosedTrade> closedTrades) {
        Account account = accounts.get(accountId);
        if (account != null && closedTrades != null) {
            account.setClosedTrades(closedTrades);
            account.setLastSeen(LocalDateTime.now());
        }
    }
}
