package de.trademonitor.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores EA (Expert Advisor) log file entries transmitted from MetaTrader.
 * These are the log lines from the "Expert" tab in MetaTrader.
 */
@Entity
@Table(indexes = {
    @Index(name = "idx_ealog_account_ts", columnList = "accountId, timestamp")
})
public class EaLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;

    private LocalDateTime timestamp;

    @Column(length = 4000)
    private String logLine;

    public EaLogEntry() {
    }

    public EaLogEntry(Long accountId, String logLine) {
        this.accountId = accountId;
        this.logLine = logLine;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getLogLine() {
        return logLine;
    }

    public void setLogLine(String logLine) {
        this.logLine = logLine;
    }
}
