package de.trademonitor.dto;

import java.util.Map;

/**
 * DTO for per-magic-number profit summary.
 */
public class MagicProfitEntry {
    private long magicNumber;
    private double openProfit;
    private double closedProfit;
    private double totalProfit;
    private String magicName;
    private int openTradeCount;
    private int closedTradeCount;

    private double totalSwap;
    private double totalCommission;

    private Map<String, Integer> tradedSymbols;
    private double maxDrawdownEur;
    private double maxDrawdownPercent;
    private double maxEquityDrawdownEur;
    private double maxEquityDrawdownPercent;

    public MagicProfitEntry(long magicNumber, String magicName, double openProfit, double closedProfit,
            double totalSwap, double totalCommission,
            int openTradeCount, int closedTradeCount, Map<String, Integer> tradedSymbols,
            double maxDrawdownEur, double maxDrawdownPercent,
            double maxEquityDrawdownEur, double maxEquityDrawdownPercent) {
        this.magicNumber = magicNumber;
        this.magicName = magicName;
        this.openProfit = openProfit;
        this.closedProfit = closedProfit;
        this.totalSwap = totalSwap;
        this.totalCommission = totalCommission;
        this.totalProfit = openProfit + closedProfit + totalSwap + totalCommission;
        this.openTradeCount = openTradeCount;
        this.closedTradeCount = closedTradeCount;
        this.tradedSymbols = tradedSymbols;
        this.maxDrawdownEur = maxDrawdownEur;
        this.maxDrawdownPercent = maxDrawdownPercent;
        this.maxEquityDrawdownEur = maxEquityDrawdownEur;
        this.maxEquityDrawdownPercent = maxEquityDrawdownPercent;
    }

    public long getMagicNumber() {
        return magicNumber;
    }

    public String getMagicName() {
        return magicName;
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

    public double getTotalSwap() {
        return totalSwap;
    }

    public double getTotalCommission() {
        return totalCommission;
    }

    public int getOpenTradeCount() {
        return openTradeCount;
    }

    public int getClosedTradeCount() {
        return closedTradeCount;
    }

    public Map<String, Integer> getTradedSymbols() {
        return tradedSymbols;
    }

    public double getMaxDrawdownEur() {
        return maxDrawdownEur;
    }

    public double getMaxDrawdownPercent() {
        return maxDrawdownPercent;
    }

    public double getMaxEquityDrawdownEur() {
        return maxEquityDrawdownEur;
    }

    public double getMaxEquityDrawdownPercent() {
        return maxEquityDrawdownPercent;
    }

    public String getTradedSymbolsJson() {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(tradedSymbols != null ? tradedSymbols : new java.util.HashMap<>());
        } catch (Exception e) {
            return "{}";
        }
    }
}
