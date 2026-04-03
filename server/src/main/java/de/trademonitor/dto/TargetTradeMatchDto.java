package de.trademonitor.dto;

import de.trademonitor.model.Trade;

public class TargetTradeMatchDto {
    private Trade targetTrade;
    private boolean isMatched;
    private boolean isExempt;
    private String matchedBySourceName;
    private Long matchedBySourceTicket;
    private Boolean isStage2Match;
    private Boolean isStage3Match;

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

    public Boolean getIsStage2Match() {
        return isStage2Match;
    }

    public void setIsStage2Match(Boolean isStage2Match) {
        this.isStage2Match = isStage2Match;
    }

    public Boolean getIsStage3Match() {
        return isStage3Match;
    }

    public void setIsStage3Match(Boolean isStage3Match) {
        this.isStage3Match = isStage3Match;
    }
}
