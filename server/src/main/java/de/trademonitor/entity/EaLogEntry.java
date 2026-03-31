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
        this.timestamp = parseTimestampFromLogLine(logLine);
    }

    private LocalDateTime parseTimestampFromLogLine(String line) {
        LocalDateTime now = LocalDateTime.now();
        if (line == null || line.trim().isEmpty()) {
            return now;
        }

        try {
            // Check for Date + Time format: "2026.03.29 23:01:16.123"
            java.util.regex.Pattern fullPattern = java.util.regex.Pattern.compile("(\\d{4})\\.(\\d{2})\\.(\\d{2})\\s+(\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{3}))?");
            java.util.regex.Matcher fullMatcher = fullPattern.matcher(line);
            if (fullMatcher.find()) {
                int year = Integer.parseInt(fullMatcher.group(1));
                int month = Integer.parseInt(fullMatcher.group(2));
                int day = Integer.parseInt(fullMatcher.group(3));
                int hour = Integer.parseInt(fullMatcher.group(4));
                int min = Integer.parseInt(fullMatcher.group(5));
                int sec = Integer.parseInt(fullMatcher.group(6));
                int nano = fullMatcher.group(7) != null ? Integer.parseInt(fullMatcher.group(7)) * 1000000 : 0;
                return LocalDateTime.of(year, month, day, hour, min, sec, nano);
            }

            // Check for Time only format: "MR 0 22:59:35.457"
            java.util.regex.Pattern timePattern = java.util.regex.Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{3}))?");
            java.util.regex.Matcher timeMatcher = timePattern.matcher(line);
            if (timeMatcher.find()) {
                int hour = Integer.parseInt(timeMatcher.group(1));
                int min = Integer.parseInt(timeMatcher.group(2));
                int sec = Integer.parseInt(timeMatcher.group(3));
                int nano = timeMatcher.group(4) != null ? Integer.parseInt(timeMatcher.group(4)) * 1000000 : 0;

                LocalDateTime parsedContent = LocalDateTime.of(now.toLocalDate(), java.time.LocalTime.of(hour, min, sec, nano));
                
                // Handle midnight crossover if log is older than server's midnight
                if (now.getHour() < 2 && hour >= 22) {
                    parsedContent = parsedContent.minusDays(1);
                } else if (now.getHour() >= 22 && hour < 2) {
                    // Very rate, server is behind EA
                    parsedContent = parsedContent.plusDays(1);
                }
                return parsedContent;
            }
        } catch (Exception e) {
            // fallback to now
        }
        
        return now;
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
