package de.trademonitor.dto;

/**
 * DTO for heartbeat request.
 */
public class HeartbeatRequest {
    private long accountId;
    private String timestamp;

    // Getters and Setters
    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
