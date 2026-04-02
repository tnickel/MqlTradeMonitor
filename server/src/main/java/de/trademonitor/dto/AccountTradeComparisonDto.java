package de.trademonitor.dto;

import de.trademonitor.entity.ClosedTradeEntity;

/**
 * DTO for comparing trades between two arbitrary accounts (A and B).
 * Each row represents a matched pair or an unmatched trade.
 */
public class AccountTradeComparisonDto {

    private ClosedTradeEntity tradeA;
    private ClosedTradeEntity tradeB;

    /**
     * MATCHED   – trade found in both A and B within tolerance
     * ONLY_A    – trade exists only in account A
     * ONLY_B    – trade exists only in account B
     */
    private String matchStatus;

    /** Time difference in seconds between open times (abs value). Null if unmatched. */
    private Long timeDiffSeconds;

    // Slippage (A vs B). Null if unmatched.
    private Double openSlippage;
    private Double closeSlippage;
    private String openSlippageFormatted;
    private String closeSlippageFormatted;

    public AccountTradeComparisonDto() {
    }

    // --- Getters and Setters ---

    public ClosedTradeEntity getTradeA() {
        return tradeA;
    }

    public void setTradeA(ClosedTradeEntity tradeA) {
        this.tradeA = tradeA;
    }

    public ClosedTradeEntity getTradeB() {
        return tradeB;
    }

    public void setTradeB(ClosedTradeEntity tradeB) {
        this.tradeB = tradeB;
    }

    public String getMatchStatus() {
        return matchStatus;
    }

    public void setMatchStatus(String matchStatus) {
        this.matchStatus = matchStatus;
    }

    public Long getTimeDiffSeconds() {
        return timeDiffSeconds;
    }

    public void setTimeDiffSeconds(Long timeDiffSeconds) {
        this.timeDiffSeconds = timeDiffSeconds;
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
}
