package de.trademonitor.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class ClientErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;
    private Long accountId;
    private String action;
    private String ipAddress;

    @Column(length = 2000)
    private String message;

    public ClientErrorLog() {
    }

    public ClientErrorLog(Long accountId, String action, String ipAddress, String message) {
        this.timestamp = LocalDateTime.now();
        this.accountId = accountId;
        this.action = action;
        this.ipAddress = ipAddress;
        this.message = message;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
