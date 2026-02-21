package de.trademonitor.dto;

import de.trademonitor.entity.ClosedTradeEntity;

public class TradeComparisonDto {

    private ClosedTradeEntity realTrade;
    private ClosedTradeEntity demoTrade;

    private String realAccountName;
    private String demoAccountName;

    private Long openDelaySeconds;
    private Long closeDelaySeconds;
    private Double openSlippage;
    private Double closeSlippage;

    private String openSlippageFormatted;
    private String closeSlippageFormatted;

    // Status: "MATCHED", "NOT FOUND"
    private String status;

    public TradeComparisonDto() {
    }

    public ClosedTradeEntity getRealTrade() {
        return realTrade;
    }

    public void setRealTrade(ClosedTradeEntity realTrade) {
        this.realTrade = realTrade;
    }

    public ClosedTradeEntity getDemoTrade() {
        return demoTrade;
    }

    public void setDemoTrade(ClosedTradeEntity demoTrade) {
        this.demoTrade = demoTrade;
    }

    public String getRealAccountName() {
        return realAccountName;
    }

    public void setRealAccountName(String realAccountName) {
        this.realAccountName = realAccountName;
    }

    public String getDemoAccountName() {
        return demoAccountName;
    }

    public void setDemoAccountName(String demoAccountName) {
        this.demoAccountName = demoAccountName;
    }

    public Long getOpenDelaySeconds() {
        return openDelaySeconds;
    }

    public void setOpenDelaySeconds(Long openDelaySeconds) {
        this.openDelaySeconds = openDelaySeconds;
    }

    public Long getCloseDelaySeconds() {
        return closeDelaySeconds;
    }

    public void setCloseDelaySeconds(Long closeDelaySeconds) {
        this.closeDelaySeconds = closeDelaySeconds;
    }

    public Double getOpenSlippage() {
        return openSlippage;
    }

    public void setOpenSlippage(Double openSlippage) {
        this.openSlippage = openSlippage;
    }

    public Double getCloseSlippage() {
        return closeSlippage;
    }

    public void setCloseSlippage(Double closeSlippage) {
        this.closeSlippage = closeSlippage;
    }

    public String getOpenSlippageFormatted() {
        return openSlippageFormatted;
    }

    public void setOpenSlippageFormatted(String openSlippageFormatted) {
        this.openSlippageFormatted = openSlippageFormatted;
    }

    public String getCloseSlippageFormatted() {
        return closeSlippageFormatted;
    }

    public void setCloseSlippageFormatted(String closeSlippageFormatted) {
        this.closeSlippageFormatted = closeSlippageFormatted;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
