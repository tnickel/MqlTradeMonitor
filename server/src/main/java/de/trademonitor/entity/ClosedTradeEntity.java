package de.trademonitor.entity;

import jakarta.persistence.*;

/**
 * JPA entity for closed/historical trades, persisted in H2.
 * Composite unique key: accountId + ticket (a trade ticket is unique per
 * account).
 */
@Entity
@Table(name = "closed_trades", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "accountId", "ticket" })
}, indexes = {
        @Index(name = "idx_closed_trades_account", columnList = "accountId"),
        @Index(name = "idx_closed_trades_account_close", columnList = "accountId, closeTime"),
        @Index(name = "idx_closed_trades_magic", columnList = "magicNumber")
})
public class ClosedTradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long accountId;
    private long ticket;
    private String symbol;
    private String type;
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
    @Column(nullable = true)
    private Double sl;
    private Long openTimeMsc;
    private Long closeTimeMsc;
    private Double openAsk;
    private Double openBid;
    private Double closeAsk;
    private Double closeBid;
    private Long openOrderSetupTimeMsc;
    private Long closeOrderSetupTimeMsc;
    @Column(columnDefinition = "TEXT")
    private String openTicks;
    @Column(columnDefinition = "TEXT")
    private String closeTicks;

    public ClosedTradeEntity() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

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
}
