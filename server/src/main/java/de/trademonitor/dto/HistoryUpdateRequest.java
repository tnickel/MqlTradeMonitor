package de.trademonitor.dto;

import de.trademonitor.model.ClosedTrade;
import java.util.List;

/**
 * DTO for history update request.
 */
public class HistoryUpdateRequest {
    private long accountId;
    private List<ClosedTrade> closedTrades;
    private String timestamp;

    // Getters and Setters
    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public List<ClosedTrade> getClosedTrades() {
        return closedTrades;
    }

    public void setClosedTrades(List<ClosedTrade> closedTrades) {
        this.closedTrades = closedTrades;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
