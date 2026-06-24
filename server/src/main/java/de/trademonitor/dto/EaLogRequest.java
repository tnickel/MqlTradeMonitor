package de.trademonitor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * DTO for incoming EA logs from the client.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EaLogRequest {
    private long accountId;
    private List<String> logEntries;

    // Default constructor
    public EaLogRequest() {
    }

    public EaLogRequest(long accountId, List<String> logEntries) {
        this.accountId = accountId;
        this.logEntries = logEntries;
    }

    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public List<String> getLogEntries() {
        return logEntries;
    }

    public void setLogEntries(List<String> logEntries) {
        this.logEntries = logEntries;
    }
}
