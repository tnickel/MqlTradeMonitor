package de.trademonitor.dto;

import de.trademonitor.model.ClosedTrade;
import de.trademonitor.model.Trade;
import java.util.List;

/**
 * DTO for initial trade list upload (bulk transfer on first connect).
 * Contains both open trades and closed trade history.
 */
public class TradeInitRequest {
    private long accountId;
    private List<Trade> trades;
    private List<ClosedTrade> closedTrades;
    private double equity;
    private double balance;
    private String timestamp;

    // Getters and Setters
    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public List<Trade> getTrades() {
        return trades;
    }

    public void setTrades(List<Trade> trades) {
        this.trades = trades;
    }

    public List<ClosedTrade> getClosedTrades() {
        return closedTrades;
    }

    public void setClosedTrades(List<ClosedTrade> closedTrades) {
        this.closedTrades = closedTrades;
    }

    public double getEquity() {
        return equity;
    }

    public void setEquity(double equity) {
        this.equity = equity;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
