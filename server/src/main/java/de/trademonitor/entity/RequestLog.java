package de.trademonitor.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class RequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;
    private String ipAddress;
    private String method;
    private String uri;

    @Column(length = 1000)
    private String queryString;

    @Column(length = 1000)
    private String userAgent;

    private int statusCode;

    public RequestLog() {
    }

    public RequestLog(String ipAddress, String method, String uri, String queryString, String userAgent,
            int statusCode) {
        this.timestamp = LocalDateTime.now();
        this.ipAddress = ipAddress;
        this.method = method;
        this.uri = uri;
        this.queryString = queryString;
        this.userAgent = userAgent;
        this.statusCode = statusCode;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }
}
