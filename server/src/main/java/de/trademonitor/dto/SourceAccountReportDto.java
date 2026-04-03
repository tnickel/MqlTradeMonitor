package de.trademonitor.dto;

import de.trademonitor.model.Trade;

import java.util.List;

public class SourceAccountReportDto {
    private long sourceAccountId;
    private String sourceAccountName;
    private List<Trade> sourceTrades;
    private long gmtOffsetSeconds;

    public SourceAccountReportDto() {
    }

    public long getSourceAccountId() {
        return sourceAccountId;
    }

    public void setSourceAccountId(long sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    public String getSourceAccountName() {
        return sourceAccountName;
    }

    public void setSourceAccountName(String sourceAccountName) {
        this.sourceAccountName = sourceAccountName;
    }

    public List<Trade> getSourceTrades() {
        return sourceTrades;
    }

    public void setSourceTrades(List<Trade> sourceTrades) {
        this.sourceTrades = sourceTrades;
    }

    public long getGmtOffsetSeconds() {
        return gmtOffsetSeconds;
    }

    public void setGmtOffsetSeconds(long gmtOffsetSeconds) {
        this.gmtOffsetSeconds = gmtOffsetSeconds;
    }
}
