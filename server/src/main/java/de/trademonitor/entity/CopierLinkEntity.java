package de.trademonitor.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "copier_links")
public class CopierLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_account_id", nullable = false)
    private Long sourceAccountId;

    @Column(name = "target_account_id", nullable = false)
    private Long targetAccountId;

    @Column(name = "status")
    private String status = "ACTIVE"; // e.g. ACTIVE, INACTIVE

    public CopierLinkEntity() {
    }

    public CopierLinkEntity(Long sourceAccountId, Long targetAccountId) {
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    public void setSourceAccountId(Long sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    public Long getTargetAccountId() {
        return targetAccountId;
    }

    public void setTargetAccountId(Long targetAccountId) {
        this.targetAccountId = targetAccountId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
