package de.trademonitor.dto;

public class MagicDrawdownItem {
    private long accountId;
    private String accountName;
    private String accountType; // REAL/DEMO
    private boolean isReal;

    private long magicNumber;
    private String magicName;

    private double currentDrawdownEur;
    private double currentDrawdownPercent;

    private double drawdownEur;
    private double drawdownPercent;
    private double equityDrawdownEur;
    private double equityDrawdownPercent;
    private double openProfit;

    private double balanceHigh;
    private double currentMagicEquity; // balance + open profit for this magic

    private Long lastSeenMins;
    private String lastSeenString;

    public MagicDrawdownItem() {
    }

    public MagicDrawdownItem(long accountId, String accountName, String accountType, long magicNumber, String magicName,
            double ddEur, double ddPercent, double balanceHigh, double currentMagicEq) {
        this.accountId = accountId;
        this.accountName = accountName;
        this.accountType = accountType;
        this.isReal = "REAL".equalsIgnoreCase(accountType);
        this.magicNumber = magicNumber;
        this.magicName = magicName;
        this.currentDrawdownEur = ddEur;
        this.currentDrawdownPercent = ddPercent;
        this.drawdownEur = ddEur;
        this.drawdownPercent = ddPercent;
        this.equityDrawdownEur = ddEur;
        this.equityDrawdownPercent = ddPercent;
        this.openProfit = currentMagicEq;
        this.balanceHigh = balanceHigh;
        this.currentMagicEquity = currentMagicEq;
    }

    // Getters and Setters
    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
        this.isReal = "REAL".equalsIgnoreCase(accountType);
    }

    public boolean isReal() {
        return isReal;
    }

    public void setReal(boolean real) {
        isReal = real;
    }

    public long getMagicNumber() {
        return magicNumber;
    }

    public void setMagicNumber(long magicNumber) {
        this.magicNumber = magicNumber;
    }

    public String getMagicName() {
        return magicName;
    }

    public void setMagicName(String magicName) {
        this.magicName = magicName;
    }

    public double getCurrentDrawdownEur() {
        return currentDrawdownEur;
    }

    public void setCurrentDrawdownEur(double currentDrawdownEur) {
        this.currentDrawdownEur = currentDrawdownEur;
    }

    public double getCurrentDrawdownPercent() {
        return currentDrawdownPercent;
    }

    public void setCurrentDrawdownPercent(double currentDrawdownPercent) {
        this.currentDrawdownPercent = currentDrawdownPercent;
    }

    public double getDrawdownEur() {
        return drawdownEur;
    }

    public void setDrawdownEur(double drawdownEur) {
        this.drawdownEur = drawdownEur;
    }

    public double getDrawdownPercent() {
        return drawdownPercent;
    }

    public void setDrawdownPercent(double drawdownPercent) {
        this.drawdownPercent = drawdownPercent;
    }

    public double getEquityDrawdownEur() {
        return equityDrawdownEur;
    }

    public void setEquityDrawdownEur(double equityDrawdownEur) {
        this.equityDrawdownEur = equityDrawdownEur;
    }

    public double getEquityDrawdownPercent() {
        return equityDrawdownPercent;
    }

    public void setEquityDrawdownPercent(double equityDrawdownPercent) {
        this.equityDrawdownPercent = equityDrawdownPercent;
    }

    public double getOpenProfit() {
        return openProfit;
    }

    public void setOpenProfit(double openProfit) {
        this.openProfit = openProfit;
    }

    public double getBalanceHigh() {
        return balanceHigh;
    }

    public void setBalanceHigh(double balanceHigh) {
        this.balanceHigh = balanceHigh;
    }

    public double getCurrentMagicEquity() {
        return currentMagicEquity;
    }

    public void setCurrentMagicEquity(double currentMagicEquity) {
        this.currentMagicEquity = currentMagicEquity;
    }

    public Long getLastSeenMins() {
        return lastSeenMins;
    }

    public void setLastSeenMins(Long lastSeenMins) {
        this.lastSeenMins = lastSeenMins;
    }

    public String getLastSeenString() {
        return lastSeenString;
    }

    public void setLastSeenString(String lastSeenString) {
        this.lastSeenString = lastSeenString;
    }
}
