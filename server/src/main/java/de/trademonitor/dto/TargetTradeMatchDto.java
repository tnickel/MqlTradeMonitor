package de.trademonitor.dto;

import de.trademonitor.model.Trade;

public class TargetTradeMatchDto {
    private Trade targetTrade;
    private boolean isMatched;
    private boolean isExempt;
    private String matchedBySourceName;
    private Long matchedBySourceTicket;

    public TargetTradeMatchDto() {
    }

    public Trade getTargetTrade() {
        return targetTrade;
    }

    public void setTargetTrade(Trade targetTrade) {
        this.targetTrade = targetTrade;
    }

    public boolean getIsMatched() {
        return isMatched;
    }

    public void setIsMatched(boolean matched) {
        isMatched = matched;
    }

    public String getMatchedBySourceName() {
        return matchedBySourceName;
    }

    public void setMatchedBySourceName(String matchedBySourceName) {
        this.matchedBySourceName = matchedBySourceName;
    }

    public Long getMatchedBySourceTicket() {
        return matchedBySourceTicket;
    }

    public void setMatchedBySourceTicket(Long matchedBySourceTicket) {
        this.matchedBySourceTicket = matchedBySourceTicket;
    }

    public boolean getIsExempt() {
        return isExempt;
    }

    public void setIsExempt(boolean exempt) {
        isExempt = exempt;
    }
}
