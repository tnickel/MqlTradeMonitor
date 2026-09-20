package de.trademonitor.entity;

import jakarta.persistence.*;

/**
 * JPA entity: one MqlKiScanner sync run (register → … → complete/abort).
 * The dashboard tile shows the latest run to indicate the connection state.
 */
@Entity
@Table(name = "ki_sync_runs")
public class KiSyncRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private java.time.LocalDateTime startedAt;

    @Column(name = "finished_at")
    private java.time.LocalDateTime finishedAt;

    /** running | ok | aborted. */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "running";

    @Column(name = "scanner_version", length = 32)
    private String scannerVersion;

    @Column(name = "signals_stored")
    private int signalsStored;

    @Column(name = "documents_total")
    private int documentsTotal;

    @Column(name = "documents_uploaded")
    private int documentsUploaded;

    @Column(name = "documents_skipped")
    private int documentsSkipped;

    @Column(name = "bytes_transferred")
    private long bytesTransferred;

    /** Abort reason or note. */
    @Column(name = "note", length = 1024)
    private String note;

    public KiSyncRunEntity() {
    }

    public KiSyncRunEntity(String scannerVersion) {
        this.startedAt = java.time.LocalDateTime.now();
        this.scannerVersion = scannerVersion;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public java.time.LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(java.time.LocalDateTime startedAt) { this.startedAt = startedAt; }

    public java.time.LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(java.time.LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getScannerVersion() { return scannerVersion; }
    public void setScannerVersion(String scannerVersion) { this.scannerVersion = scannerVersion; }

    public int getSignalsStored() { return signalsStored; }
    public void setSignalsStored(int signalsStored) { this.signalsStored = signalsStored; }

    public int getDocumentsTotal() { return documentsTotal; }
    public void setDocumentsTotal(int documentsTotal) { this.documentsTotal = documentsTotal; }

    public int getDocumentsUploaded() { return documentsUploaded; }
    public void setDocumentsUploaded(int documentsUploaded) { this.documentsUploaded = documentsUploaded; }

    public int getDocumentsSkipped() { return documentsSkipped; }
    public void setDocumentsSkipped(int documentsSkipped) { this.documentsSkipped = documentsSkipped; }

    public long getBytesTransferred() { return bytesTransferred; }
    public void setBytesTransferred(long bytesTransferred) { this.bytesTransferred = bytesTransferred; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
