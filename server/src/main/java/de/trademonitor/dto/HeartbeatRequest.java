package de.trademonitor.dto;

/**
 * DTO for heartbeat request.
 */
public class HeartbeatRequest {
    private long accountId;
    private String timestamp;
    private String version;

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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
