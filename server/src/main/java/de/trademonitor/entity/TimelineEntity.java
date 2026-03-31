package de.trademonitor.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "timelines")
public class TimelineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id")
    private long accountId;

    @Column(name = "timeline_date")
    private String timelineDate; // Format: yyyy-MM-dd

    public TimelineEntity() {}

    public TimelineEntity(long accountId, String timelineDate) {
        this.accountId = accountId;
        this.timelineDate = timelineDate;
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

    public String getTimelineDate() {
        return timelineDate;
    }

    public void setTimelineDate(String timelineDate) {
        this.timelineDate = timelineDate;
    }
}
