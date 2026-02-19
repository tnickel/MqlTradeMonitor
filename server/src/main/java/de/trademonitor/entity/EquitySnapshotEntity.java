package de.trademonitor.entity;

import jakarta.persistence.*;

/**
 * Persisted equity snapshot for a specific account at a point in time.
 * Used to draw the equity curve in the account detail chart.
 */
@Entity
@Table(name = "equity_snapshots", indexes = {
        @Index(name = "idx_equity_snapshot_account_time", columnList = "accountId, timestamp")
})
public class EquitySnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long accountId;

    /** ISO timestamp string, e.g. "2026-02-19T19:00:00" */
    @Column(nullable = false, length = 30)
    private String timestamp;

    private double equity;
    private double balance;

    public EquitySnapshotEntity() {
    }

    public EquitySnapshotEntity(long accountId, String timestamp, double equity, double balance) {
        this.accountId = accountId;
        this.timestamp = timestamp;
        this.equity = equity;
        this.balance = balance;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public double getEquity() {
        return equity;
    }

    public void setEquity(double equity) {
        this.equity = equity;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }
}
