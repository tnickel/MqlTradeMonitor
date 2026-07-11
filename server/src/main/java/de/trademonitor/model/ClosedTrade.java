package de.trademonitor.model;

/**
 * Represents a closed/historical trade.
 */
public class ClosedTrade {
    private long ticket;
    private String symbol;
    private String type; // BUY or SELL
    private double volume;
    private double openPrice;
    private double closePrice;
    private String openTime;
    private String closeTime;
    private double profit;
    private double swap;
    private double commission;
    private long magicNumber;
    private String comment;
    private Double sl;
    private Long openTimeMsc;
    private Long closeTimeMsc;
    private Double openAsk;
    private Double openBid;
    private Double closeAsk;
    private Double closeBid;
    private Long openOrderSetupTimeMsc;
    private Long closeOrderSetupTimeMsc;
    private String openTicks;
    private String closeTicks;
    private String candlesM5;
    private String candlesM15;
    private String candlesH1;

    public ClosedTrade() {
    }

    // Getters and Setters
    public long getTicket() {
        return ticket;
    }

    public void setTicket(long ticket) {
        this.ticket = ticket;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(double openPrice) {
        this.openPrice = openPrice;
    }

    public double getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(double closePrice) {
        this.closePrice = closePrice;
    }

    public String getOpenTime() {
        return openTime;
    }

    public void setOpenTime(String openTime) {
        this.openTime = openTime;
    }

    public String getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(String closeTime) {
        this.closeTime = closeTime;
    }

    public double getProfit() {
        return profit;
    }

    public void setProfit(double profit) {
        this.profit = profit;
    }

    public double getSwap() {
        return swap;
    }

    public void setSwap(double swap) {
        this.swap = swap;
    }

    public double getCommission() {
        return commission;
    }

    public void setCommission(double commission) {
        this.commission = commission;
    }

    public long getMagicNumber() {
        return magicNumber;
    }

    public void setMagicNumber(long magicNumber) {
        this.magicNumber = magicNumber;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Double getSl() {
        return sl;
    }

    public void setSl(Double sl) {
        this.sl = sl;
    }

    public Long getOpenTimeMsc() {
        return openTimeMsc;
    }

    public void setOpenTimeMsc(Long openTimeMsc) {
        this.openTimeMsc = openTimeMsc;
    }

    public Long getCloseTimeMsc() {
        return closeTimeMsc;
    }

    public void setCloseTimeMsc(Long closeTimeMsc) {
        this.closeTimeMsc = closeTimeMsc;
    }

    public Double getOpenAsk() {
        return openAsk;
    }

    public void setOpenAsk(Double openAsk) {
        this.openAsk = openAsk;
    }

    public Double getOpenBid() {
        return openBid;
    }

    public void setOpenBid(Double openBid) {
        this.openBid = openBid;
    }

    public Double getCloseAsk() {
        return closeAsk;
    }

    public void setCloseAsk(Double closeAsk) {
        this.closeAsk = closeAsk;
    }

    public Double getCloseBid() {
        return closeBid;
    }

    public void setCloseBid(Double closeBid) {
        this.closeBid = closeBid;
    }

    public Long getOpenOrderSetupTimeMsc() {
        return openOrderSetupTimeMsc;
    }

    public void setOpenOrderSetupTimeMsc(Long openOrderSetupTimeMsc) {
        this.openOrderSetupTimeMsc = openOrderSetupTimeMsc;
    }

    public Long getCloseOrderSetupTimeMsc() {
        return closeOrderSetupTimeMsc;
    }

    public void setCloseOrderSetupTimeMsc(Long closeOrderSetupTimeMsc) {
        this.closeOrderSetupTimeMsc = closeOrderSetupTimeMsc;
    }

    public String getOpenTicks() {
        return openTicks;
    }

    public void setOpenTicks(String openTicks) {
        this.openTicks = openTicks;
    }

    public String getCloseTicks() {
        return closeTicks;
    }

    public void setCloseTicks(String closeTicks) {
        this.closeTicks = closeTicks;
    }

    public String getCandlesM5() {
        return candlesM5;
    }

    public void setCandlesM5(String candlesM5) {
        this.candlesM5 = candlesM5;
    }

    public String getCandlesM15() {
        return candlesM15;
    }

    public void setCandlesM15(String candlesM15) {
        this.candlesM15 = candlesM15;
    }

    public String getCandlesH1() {
        return candlesH1;
    }

    public void setCandlesH1(String candlesH1) {
        this.candlesH1 = candlesH1;
    }
}
