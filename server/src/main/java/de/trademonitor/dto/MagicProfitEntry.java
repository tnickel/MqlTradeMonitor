package de.trademonitor.dto;

/**
 * DTO for per-magic-number profit summary.
 */
public class MagicProfitEntry {
    private long magicNumber;
    private double openProfit;
    private double closedProfit;
    private double totalProfit;
    private int openTradeCount;
    private int closedTradeCount;

    public MagicProfitEntry(long magicNumber, double openProfit, double closedProfit,
            int openTradeCount, int closedTradeCount) {
        this.magicNumber = magicNumber;
        this.openProfit = openProfit;
        this.closedProfit = closedProfit;
        this.totalProfit = openProfit + closedProfit;
        this.openTradeCount = openTradeCount;
        this.closedTradeCount = closedTradeCount;
    }

    public long getMagicNumber() {
        return magicNumber;
    }

    public double getOpenProfit() {
        return openProfit;
    }

    public double getClosedProfit() {
        return closedProfit;
    }

    public double getTotalProfit() {
        return totalProfit;
    }

    public int getOpenTradeCount() {
        return openTradeCount;
    }

    public int getClosedTradeCount() {
        return closedTradeCount;
    }
}
