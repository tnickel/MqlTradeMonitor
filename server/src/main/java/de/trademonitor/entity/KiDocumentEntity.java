package de.trademonitor.entity;

import jakarta.persistence.*;

/**
 * JPA entity: one PDF document transferred by the MqlKiScanner sync
 * (own reports, deep analyses, portfolio report and mirrored downloader
 * test reports). Stored as a BLOB like account documents.
 */
@Entity
@Table(name = "ki_documents")
public class KiDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable key from the scanner, e.g. signal/2349227/03-gesamtbericht.pdf. */
    @Column(name = "doc_key", nullable = false, unique = true, length = 512)
    private String docKey;

    /** Owning MQL5 signal id; null for the portfolio report. */
    @Column(name = "signal_id")
    private Long signalId;

    /** eigene | downloader | portfolio. */
    @Column(name = "group_name", nullable = false, length = 32)
    private String group;

    /** trade_analyse | risiko_analyse | gesamtbericht | tiefenanalyse |
     *  portfolio | downloader_testreport. */
    @Column(name = "kind", length = 64)
    private String kind;

    @Column(name = "label", length = 255)
    private String label;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", length = 64)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    /** SHA-256 of the transferred bytes (hex) — enables the client-side diff. */
    @Column(name = "sha256", length = 64)
    private String sha256;

    /** Source timestamp as reported by the scanner. */
    @Column(name = "last_modified", length = 64)
    private String lastModified;

    @Lob
    @Column(name = "file_data", columnDefinition = "BLOB")
    private byte[] fileData;

    @Column(name = "updated_at", nullable = false)
    private java.time.LocalDateTime updatedAt;

    public KiDocumentEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDocKey() { return docKey; }
    public void setDocKey(String docKey) { this.docKey = docKey; }

    public Long getSignalId() { return signalId; }
    public void setSignalId(Long signalId) { this.signalId = signalId; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public String getLastModified() { return lastModified; }
    public void setLastModified(String lastModified) { this.lastModified = lastModified; }

    public byte[] getFileData() { return fileData; }
    public void setFileData(byte[] fileData) { this.fileData = fileData; }

    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
