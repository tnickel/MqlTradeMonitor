package de.trademonitor.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SecurityAuditDto {

    public enum Status { GREEN, YELLOW, RED }

    private LocalDateTime checkTime;
    private Status overallStatus = Status.GREEN;
    private String statusMessage = "";

    // SSH Brute-Force
    private int failedSshCount;
    private List<IpCount> topAttackerIps = new ArrayList<>();

    // Fail2Ban
    private int fail2banBannedCount;
    private List<String> fail2banBannedIps = new ArrayList<>();
    private int fail2banTotalBans;

    // Nginx suspicious requests
    private int suspiciousRequestCount;
    private List<String> suspiciousRequests = new ArrayList<>();

    // Nginx top IPs
    private List<IpCount> topRequestIps = new ArrayList<>();

    // Port check
    private List<String> openPorts = new ArrayList<>();
    private List<String> unexpectedPorts = new ArrayList<>();

    // UFW
    private boolean ufwActive;
    private String ufwRules = "";

    // Recent logins
    private List<String> recentLogins = new ArrayList<>();

    // Inner class for IP + count
    public static class IpCount {
        private String ip;
        private int count;

        public IpCount() {}
        public IpCount(String ip, int count) {
            this.ip = ip;
            this.count = count;
        }

        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    // Getters and setters
    public LocalDateTime getCheckTime() { return checkTime; }
    public void setCheckTime(LocalDateTime checkTime) { this.checkTime = checkTime; }

    public Status getOverallStatus() { return overallStatus; }
    public void setOverallStatus(Status overallStatus) { this.overallStatus = overallStatus; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    public int getFailedSshCount() { return failedSshCount; }
    public void setFailedSshCount(int failedSshCount) { this.failedSshCount = failedSshCount; }

    public List<IpCount> getTopAttackerIps() { return topAttackerIps; }
    public void setTopAttackerIps(List<IpCount> topAttackerIps) { this.topAttackerIps = topAttackerIps; }

    public int getFail2banBannedCount() { return fail2banBannedCount; }
    public void setFail2banBannedCount(int fail2banBannedCount) { this.fail2banBannedCount = fail2banBannedCount; }

    public List<String> getFail2banBannedIps() { return fail2banBannedIps; }
    public void setFail2banBannedIps(List<String> fail2banBannedIps) { this.fail2banBannedIps = fail2banBannedIps; }

    public int getFail2banTotalBans() { return fail2banTotalBans; }
    public void setFail2banTotalBans(int fail2banTotalBans) { this.fail2banTotalBans = fail2banTotalBans; }

    public int getSuspiciousRequestCount() { return suspiciousRequestCount; }
    public void setSuspiciousRequestCount(int suspiciousRequestCount) { this.suspiciousRequestCount = suspiciousRequestCount; }

    public List<String> getSuspiciousRequests() { return suspiciousRequests; }
    public void setSuspiciousRequests(List<String> suspiciousRequests) { this.suspiciousRequests = suspiciousRequests; }

    public List<IpCount> getTopRequestIps() { return topRequestIps; }
    public void setTopRequestIps(List<IpCount> topRequestIps) { this.topRequestIps = topRequestIps; }

    public List<String> getOpenPorts() { return openPorts; }
    public void setOpenPorts(List<String> openPorts) { this.openPorts = openPorts; }

    public List<String> getUnexpectedPorts() { return unexpectedPorts; }
    public void setUnexpectedPorts(List<String> unexpectedPorts) { this.unexpectedPorts = unexpectedPorts; }

    public boolean isUfwActive() { return ufwActive; }
    public void setUfwActive(boolean ufwActive) { this.ufwActive = ufwActive; }

    public String getUfwRules() { return ufwRules; }
    public void setUfwRules(String ufwRules) { this.ufwRules = ufwRules; }

    public List<String> getRecentLogins() { return recentLogins; }
    public void setRecentLogins(List<String> recentLogins) { this.recentLogins = recentLogins; }
}
