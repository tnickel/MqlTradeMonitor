package de.trademonitor.entity;

import jakarta.persistence.*;

/**
 * JPA entity representing an external URL link stored for a specific MetaTrader account.
 */
@Entity
@Table(name = "account_links")
public class AccountLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private long accountId;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "min_text")
    private String minText;

    public AccountLinkEntity() {
    }

    public AccountLinkEntity(long accountId, String url, String minText) {
        this.accountId = accountId;
        this.url = url;
        this.minText = minText;
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMinText() {
        return minText;
    }

    public void setMinText(String minText) {
        this.minText = minText;
    }
}
