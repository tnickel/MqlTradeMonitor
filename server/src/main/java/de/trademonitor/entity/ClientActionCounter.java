package de.trademonitor.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"accountId", "action", "date"}))
public class ClientActionCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;
    private String action;
    private LocalDate date;
    private long count;

    public ClientActionCounter() {
    }

    public ClientActionCounter(Long accountId, String action, LocalDate date) {
        this.accountId = accountId;
        this.action = action;
        this.date = date;
        this.count = 1;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }

    public void increment() { this.count++; }
}
