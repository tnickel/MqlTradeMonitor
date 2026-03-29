package de.trademonitor.entity;

import jakarta.persistence.*;

/**
 * JPA entity for persisted account data.
 */
@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    private long accountId;

    private String broker;
    private String currency;
    private double balance;
    private double equity;
    private String name;
    private String type; // "DEMO" or "REAL"
    private String registeredAt;
    private String lastSeen;
    private String eaLogAcceptedAt; // ISO timestamp string

    private String section; // "TOP" or "BOTTOM" (Deprecated, use sectionId)

    @Column(name = "section_id")
    private Long sectionId;

    private Integer displayOrder = 0; // Ascending order

    @Column(name = "magic_number_max_age")
    private Integer magicNumberMaxAge = 30; // Default 30 days

    @Column(name = "magic_min_trades")
    private Integer magicMinTrades = 5; // Default 5 trades

    @Column(name = "open_profit_alarm_enabled", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean openProfitAlarmEnabled = false;

    @Column(name = "open_profit_alarm_abs")
    private Double openProfitAlarmAbs; // e.g. -5000 (absolute min open profit)

    @Column(name = "open_profit_alarm_pct")
    private Double openProfitAlarmPct; // e.g. 10.0 (max drawdown % of balance)

    public AccountEntity() {
    }

    public AccountEntity(long accountId, String broker, String currency, double balance) {
        this.accountId = accountId;
        this.broker = broker;
        this.currency = currency;
        this.balance = balance;
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

    public String getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(String registeredAt) {
        this.registeredAt = registeredAt;
    }

    public String getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(String lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String getEaLogAcceptedAt() {
        return eaLogAcceptedAt;
    }

    public void setEaLogAcceptedAt(String eaLogAcceptedAt) {
        this.eaLogAcceptedAt = eaLogAcceptedAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public Long getSectionId() {
        return sectionId;
    }

    public void setSectionId(Long sectionId) {
        this.sectionId = sectionId;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Integer getMagicNumberMaxAge() {
        return magicNumberMaxAge;
    }

    public void setMagicNumberMaxAge(Integer magicNumberMaxAge) {
        this.magicNumberMaxAge = magicNumberMaxAge;
    }

    public Integer getMagicMinTrades() {
        return magicMinTrades;
    }

    public void setMagicMinTrades(Integer magicMinTrades) {
        this.magicMinTrades = magicMinTrades;
    }

    public Boolean isOpenProfitAlarmEnabled() {
        return openProfitAlarmEnabled != null ? openProfitAlarmEnabled : false;
    }

    public void setOpenProfitAlarmEnabled(Boolean openProfitAlarmEnabled) {
        this.openProfitAlarmEnabled = openProfitAlarmEnabled;
    }

    public Double getOpenProfitAlarmAbs() {
        return openProfitAlarmAbs;
    }

    public void setOpenProfitAlarmAbs(Double openProfitAlarmAbs) {
        this.openProfitAlarmAbs = openProfitAlarmAbs;
    }

    public Double getOpenProfitAlarmPct() {
        return openProfitAlarmPct;
    }

    public void setOpenProfitAlarmPct(Double openProfitAlarmPct) {
        this.openProfitAlarmPct = openProfitAlarmPct;
    }
}
