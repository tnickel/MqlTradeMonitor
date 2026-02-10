package de.trademonitor.model;

import de.trademonitor.dto.MagicProfitEntry;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Represents a MetaTrader account.
 */
public class Account {
    private long accountId;
    private String broker;
    private String currency;
    private double balance;
    private double equity;
    private LocalDateTime lastSeen;
    private LocalDateTime registeredAt;
    private List<Trade> openTrades = new ArrayList<>();

    public Account() {
    }

    public Account(long accountId, String broker, String currency, double balance) {
        this.accountId = accountId;
        this.broker = broker;
        this.currency = currency;
        this.balance = balance;
        this.registeredAt = LocalDateTime.now();
        this.lastSeen = LocalDateTime.now();
    }

    // Status check
    public boolean isOnline(int timeoutSeconds) {
        if (lastSeen == null)
            return false;
        return LocalDateTime.now().minusSeconds(timeoutSeconds).isBefore(lastSeen);
    }

    // Getters and Setters
    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public String getBroker() {
        return broker;
    }

    public void setBroker(String broker) {
        this.broker = broker;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public double getEquity() {
        return equity;
    }

    public void setEquity(double equity) {
        this.equity = equity;
    }

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public List<Trade> getOpenTrades() {
        return openTrades;
    }

    public void setOpenTrades(List<Trade> openTrades) {
        this.openTrades = openTrades;
    }

    public double getTotalProfit() {
        return openTrades.stream().mapToDouble(Trade::getProfit).sum();
    }

    // Closed trades history
    private List<ClosedTrade> closedTrades = new ArrayList<>();

    public List<ClosedTrade> getClosedTrades() {
        return closedTrades;
    }

    public void setClosedTrades(List<ClosedTrade> closedTrades) {
        this.closedTrades = closedTrades;
    }

    public double getTotalHistoryProfit() {
        return closedTrades.stream().mapToDouble(ClosedTrade::getProfit).sum();
    }

    /**
     * Build a sorted list of per-magic-number profit entries,
     * combining open and closed trades.
     */
    public List<MagicProfitEntry> getMagicProfitEntries() {
        // Collect all magic numbers from both open and closed trades
        Set<Long> allMagics = new TreeSet<>();
        openTrades.forEach(t -> allMagics.add(t.getMagicNumber()));
        closedTrades.forEach(t -> allMagics.add(t.getMagicNumber()));

        List<MagicProfitEntry> entries = new ArrayList<>();
        for (Long magic : allMagics) {
            double openProfit = openTrades.stream()
                    .filter(t -> t.getMagicNumber() == magic)
                    .mapToDouble(Trade::getProfit)
                    .sum();
            int openCount = (int) openTrades.stream()
                    .filter(t -> t.getMagicNumber() == magic)
                    .count();
            double closedProfit = closedTrades.stream()
                    .filter(t -> t.getMagicNumber() == magic)
                    .mapToDouble(ClosedTrade::getProfit)
                    .sum();
            int closedCount = (int) closedTrades.stream()
                    .filter(t -> t.getMagicNumber() == magic)
                    .count();
            entries.add(new MagicProfitEntry(magic, openProfit, closedProfit, openCount, closedCount));
        }
        return entries;
    }
}
