package de.trademonitor.entity;

import jakarta.persistence.*;

/**
 * JPA entity representing an uploaded document for a specific MetaTrader account.
 */
@Entity
@Table(name = "account_documents")
public class AccountDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private long accountId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "min_text")
    private String minText;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private long fileSize;

    @Lob
    @Column(name = "file_data", columnDefinition = "BLOB")
    private byte[] fileData;

    public AccountDocumentEntity() {
    }

    public AccountDocumentEntity(long accountId, String fileName, String minText, String contentType, long fileSize, byte[] fileData) {
        this.accountId = accountId;
        this.fileName = fileName;
        this.minText = minText;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.fileData = fileData;
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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMinText() {
        return minText;
    }

    public void setMinText(String minText) {
        this.minText = minText;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public byte[] getFileData() {
        return fileData;
    }

    public void setFileData(byte[] fileData) {
        this.fileData = fileData;
    }
}
