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

    @Column(name = "real_account_id")
    private Long realAccountId;

    @Column(name = "computer_name")
    private String computerName;

    @Column(name = "login_name")
    private String loginName;

    @Column(name = "ea_version")
    private String eaVersion;

    @Column(name = "platform")
    private String platform;

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

    @Column(name = "meta_trader_info", length = 1000)
    private String metaTraderInfo;

    @Column(name = "map_pos_x")
    private Double mapPosX;

    @Column(name = "map_pos_y")
    private Double mapPosY;

    @Column(name = "server_time_offset_seconds")
    private Long serverTimeOffsetSeconds = 0L;

    @Column(name = "copier_error")
    private Boolean copierError = false;

    @Column(name = "copier_error_message", length = 1000)
    private String copierErrorMessage;

    @Column(name = "prompt_analysis_enabled", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean promptAnalysisEnabled = false;

    @Column(name = "telegram_trades_enabled", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean telegramTradesEnabled = false;

    @Column(name = "monitored", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean monitored = true;

    @Column(name = "custom_prompt", columnDefinition = "TEXT")
    private String customPrompt;

    @Column(name = "icon_base64", columnDefinition = "TEXT")
    private String iconBase64;

    @Column(name = "info_text", columnDefinition = "TEXT")
    private String infoText;

    @Column(name = "resource_order", length = 2000)
    private String resourceOrder;

    @Column(name = "last_prompt_analysis_result", columnDefinition = "TEXT")
    private String lastPromptAnalysisResult;

    @Column(name = "last_prompt_analysis_time")
    private java.time.LocalDateTime lastPromptAnalysisTime;

    public AccountEntity() {
    }

    public AccountEntity(long accountId, String broker, String currency, double balance) {
        this.accountId = accountId;
        this.broker = broker;
        this.currency = currency;
        this.balance = balance;
    }

    public Long getServerTimeOffsetSeconds() {
        return serverTimeOffsetSeconds;
    }

    public void setServerTimeOffsetSeconds(Long serverTimeOffsetSeconds) {
        this.serverTimeOffsetSeconds = serverTimeOffsetSeconds;
    }

    public Boolean getCopierError() {
        return copierError;
    }

    public void setCopierError(Boolean copierError) {
        this.copierError = copierError;
    }

    public String getCopierErrorMessage() {
        return copierErrorMessage;
    }

    public void setCopierErrorMessage(String copierErrorMessage) {
        this.copierErrorMessage = copierErrorMessage;
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

    public String getMetaTraderInfo() {
        return metaTraderInfo;
    }

    public void setMetaTraderInfo(String metaTraderInfo) {
        this.metaTraderInfo = metaTraderInfo;
    }

    public Double getMapPosX() {
        return mapPosX;
    }

    public void setMapPosX(Double mapPosX) {
        this.mapPosX = mapPosX;
    }

    public Double getMapPosY() {
        return mapPosY;
    }

    public void setMapPosY(Double mapPosY) {
        this.mapPosY = mapPosY;
    }

    public Boolean getPromptAnalysisEnabled() {
        return promptAnalysisEnabled != null ? promptAnalysisEnabled : false;
    }

    public void setPromptAnalysisEnabled(Boolean promptAnalysisEnabled) {
        this.promptAnalysisEnabled = promptAnalysisEnabled;
    }

    public String getCustomPrompt() {
        return customPrompt;
    }

    public void setCustomPrompt(String customPrompt) {
        this.customPrompt = customPrompt;
    }

    public String getLastPromptAnalysisResult() {
        return lastPromptAnalysisResult;
    }

    public void setLastPromptAnalysisResult(String lastPromptAnalysisResult) {
        this.lastPromptAnalysisResult = lastPromptAnalysisResult;
    }

    public java.time.LocalDateTime getLastPromptAnalysisTime() {
        return lastPromptAnalysisTime;
    }

    public void setLastPromptAnalysisTime(java.time.LocalDateTime lastPromptAnalysisTime) {
        this.lastPromptAnalysisTime = lastPromptAnalysisTime;
    }

    public Boolean getMonitored() {
        return monitored != null ? monitored : true;
    }

    public void setMonitored(Boolean monitored) {
        this.monitored = monitored;
    }

    public String getIconBase64() {
        return iconBase64;
    }

    public void setIconBase64(String iconBase64) {
        this.iconBase64 = iconBase64;
    }

    public Long getRealAccountId() {
        return realAccountId;
    }

    public void setRealAccountId(Long realAccountId) {
        this.realAccountId = realAccountId;
    }

    public String getComputerName() {
        return computerName;
    }

    public void setComputerName(String computerName) {
        this.computerName = computerName;
    }

    public String getLoginName() {
        return loginName;
    }

    public void setLoginName(String loginName) {
        this.loginName = loginName;
    }

    public String getEaVersion() {
        return eaVersion;
    }

    public void setEaVersion(String eaVersion) {
        this.eaVersion = eaVersion;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public Boolean getTelegramTradesEnabled() {
        return telegramTradesEnabled != null ? telegramTradesEnabled : false;
    }

    public void setTelegramTradesEnabled(Boolean telegramTradesEnabled) {
        this.telegramTradesEnabled = telegramTradesEnabled;
    }

    public String getInfoText() {
        return infoText;
    }

    public void setInfoText(String infoText) {
        this.infoText = infoText;
    }

    public String getResourceOrder() {
        return resourceOrder;
    }

    public void setResourceOrder(String resourceOrder) {
        this.resourceOrder = resourceOrder;
    }
}
