package de.trademonitor.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "llm_analysis_logs")
public class LlmAnalysisLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long accountId;
    
    private LocalDateTime timestamp;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String result;

    public LlmAnalysisLogEntity() {
    }

    public LlmAnalysisLogEntity(long accountId, String result, LocalDateTime timestamp) {
        this.accountId = accountId;
        this.result = result;
        this.timestamp = timestamp;
    }

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

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
