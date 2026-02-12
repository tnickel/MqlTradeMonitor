package de.trademonitor.dto;

/**
 * DTO for admin overview: database statistics per account.
 */
public class AccountDbStats {

    private long accountId;
    private String broker;
    private String currency;
    private long openTradeCount;
    private long closedTradeCount;
    private String earliestTradeDate;
    private String latestTradeDate;
    private double totalProfit;

    public AccountDbStats() {
    }

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

    public long getOpenTradeCount() {
        return openTradeCount;
    }

    public void setOpenTradeCount(long openTradeCount) {
        this.openTradeCount = openTradeCount;
    }

    public long getClosedTradeCount() {
        return closedTradeCount;
    }

    public void setClosedTradeCount(long closedTradeCount) {
        this.closedTradeCount = closedTradeCount;
    }

    public String getEarliestTradeDate() {
        return earliestTradeDate;
    }

    public void setEarliestTradeDate(String earliestTradeDate) {
        this.earliestTradeDate = earliestTradeDate;
    }

    public String getLatestTradeDate() {
        return latestTradeDate;
    }

    public void setLatestTradeDate(String latestTradeDate) {
        this.latestTradeDate = latestTradeDate;
    }

    public double getTotalProfit() {
        return totalProfit;
    }

    public void setTotalProfit(double totalProfit) {
        this.totalProfit = totalProfit;
    }
}
