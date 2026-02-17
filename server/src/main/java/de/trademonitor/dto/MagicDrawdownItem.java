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

    private double balanceHigh;
    private double currentMagicEquity; // balance + open profit for this magic

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
}
