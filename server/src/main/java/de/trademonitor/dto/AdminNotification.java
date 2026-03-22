package de.trademonitor.dto;

import java.time.LocalDateTime;

public class AdminNotification {

    public enum Category { SECURITY, HEALTH }
    public enum Severity { WARNING, CRITICAL }

    private LocalDateTime timestamp;
    private Category category;
    private Severity severity;
    private String title;
    private String message;
    private boolean acknowledged;

    public AdminNotification() {}

    public AdminNotification(Category category, Severity severity, String title, String message) {
        this.timestamp = LocalDateTime.now();
        this.category = category;
        this.severity = severity;
        this.title = title;
        this.message = message;
        this.acknowledged = false;
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }

    public String getCategoryIcon() {
        return category == Category.SECURITY ? "🛡️" : "🩺";
    }

    public String getSeverityColor() {
        return severity == Severity.CRITICAL ? "#f85149" : "#e3b341";
    }
}
