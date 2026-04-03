package de.trademonitor.dto;

import java.util.List;

public class CopierVerificationReportDto {
    private long targetAccountId;
    private String targetAccountName;
    private List<TargetTradeMatchDto> targetTrades;
    private List<SourceAccountReportDto> sources;
    private String role = "RECEIVER"; // "SENDER" or "RECEIVER" or "MIXED"
    private long targetGmtOffsetSeconds;

    public CopierVerificationReportDto() {
    }

    public long getTargetAccountId() {
        return targetAccountId;
    }

    public void setTargetAccountId(long targetAccountId) {
        this.targetAccountId = targetAccountId;
    }

    public String getTargetAccountName() {
        return targetAccountName;
    }

    public void setTargetAccountName(String targetAccountName) {
        this.targetAccountName = targetAccountName;
    }

    public List<TargetTradeMatchDto> getTargetTrades() {
        return targetTrades;
    }

    public void setTargetTrades(List<TargetTradeMatchDto> targetTrades) {
        this.targetTrades = targetTrades;
    }

    public List<SourceAccountReportDto> getSources() {
        return sources;
    }

    public void setSources(List<SourceAccountReportDto> sources) {
        this.sources = sources;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public long getTargetGmtOffsetSeconds() {
        return targetGmtOffsetSeconds;
    }

    public void setTargetGmtOffsetSeconds(long targetGmtOffsetSeconds) {
        this.targetGmtOffsetSeconds = targetGmtOffsetSeconds;
    }
}
