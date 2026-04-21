package de.trademonitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.trademonitor.dto.AdminNotification;
import de.trademonitor.dto.AdminNotification.Category;
import de.trademonitor.dto.AdminNotification.Severity;
import de.trademonitor.dto.SecurityAuditDto;
import de.trademonitor.dto.SecurityAuditDto.IpCount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;

@Service
public class SecurityAuditService {

    private static final String AUDIT_DIR = System.getProperty("user.home") + "/trademonitor_data";
    private static final String AUDIT_FILE = AUDIT_DIR + "/security_audit.json";
    private final ObjectMapper objectMapper;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AdminNotificationService notificationService;

    @Autowired
    private de.trademonitor.repository.LoginLogRepository loginLogRepository;

    @Autowired
    private HomeyService homeyService;

    @Autowired
    private GlobalConfigService globalConfigService;

    public SecurityAuditService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Runs daily at 3:00 AM (after log cleanup at 2:00 AM).
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledAudit() {
        System.out.println("[SecurityAudit] Starting scheduled daily security audit...");
        try {
            SecurityAuditDto result = runFullAudit();
            saveAudit(result);
            System.out.println("[SecurityAudit] Completed. Status: " + result.getOverallStatus());
            sendAuditAlertIfNeeded(result);
        } catch (Exception e) {
            System.err.println("[SecurityAudit] Error: " + e.getMessage());
        }
    }

    /**
     * Run a full audit now (manual trigger).
     */
    public SecurityAuditDto runManualAudit() {
        System.out.println("[SecurityAudit] Manual audit triggered.");
        SecurityAuditDto result = runFullAudit();
        saveAudit(result);
        sendAuditAlertIfNeeded(result);
        return result;
    }

    /**
     * Get the latest saved audit result, or null if none exists.
     */
    public SecurityAuditDto getLatestAudit() {
        File file = new File(AUDIT_FILE);
        if (!file.exists()) {
            return null;
        }
        try {
            return objectMapper.readValue(file, SecurityAuditDto.class);
        } catch (Exception e) {
            System.err.println("[SecurityAudit] Error reading audit file: " + e.getMessage());
            return null;
        }
    }

    private SecurityAuditDto runFullAudit() {
        SecurityAuditDto dto = new SecurityAuditDto();
        dto.setCheckTime(LocalDateTime.now());

        List<String> warnings = new ArrayList<>();

        // 1. SSH Brute-Force
        try {
            checkSshBruteForce(dto);
            if (dto.getFailedSshCount() > 100) {
                warnings.add(dto.getFailedSshCount() + " fehlgeschlagene SSH-Logins in auth.log");
            }
        } catch (Exception e) {
            System.err.println("[SecurityAudit] SSH check error: " + e.getMessage());
        }

        // 2. Fail2Ban
        try {
            checkFail2ban(dto);
        } catch (Exception e) {
            System.err.println("[SecurityAudit] Fail2Ban check error: " + e.getMessage());
        }

        // 3. Suspicious Nginx requests
        try {
            checkSuspiciousNginxRequests(dto);
            if (dto.getSuspiciousRequestCount() > 50) {
                warnings.add(dto.getSuspiciousRequestCount() + " verdächtige Web-Requests erkannt");
            }
        } catch (Exception e) {
            System.err.println("[SecurityAudit] Nginx suspicious check error: " + e.getMessage());
        }

        // 4. Nginx top IPs
        try {
            checkTopRequestIps(dto);
            // Check for potential DDoS (any single IP > 5000 requests)
            for (IpCount ic : dto.getTopRequestIps()) {
                if (ic.getCount() > 5000) {
                    warnings.add("IP " + ic.getIp() + " hat " + ic.getCount() + " Requests — möglicher DDoS");
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[SecurityAudit] Nginx top IPs check error: " + e.getMessage());
        }

        // 5. Open ports
        try {
            checkOpenPorts(dto);
            if (!dto.getUnexpectedPorts().isEmpty()) {
                warnings.add("Unerwartete offene Ports: " + String.join(", ", dto.getUnexpectedPorts()));
            }
        } catch (Exception e) {
            System.err.println("[SecurityAudit] Port check error: " + e.getMessage());
        }

        // 6. UFW Firewall
        try {
            checkUfw(dto);
            if (!dto.isUfwActive()) {
                warnings.add("UFW Firewall ist NICHT aktiv!");
            }
        } catch (Exception e) {
            System.err.println("[SecurityAudit] UFW check error: " + e.getMessage());
        }

        // 7. Recent logins
        try {
            checkRecentLogins(dto);
        } catch (Exception e) {
            System.err.println("[SecurityAudit] Login check error: " + e.getMessage());
        }

        // Calculate overall status
        if (!dto.isUfwActive() || !dto.getUnexpectedPorts().isEmpty()) {
            dto.setOverallStatus(SecurityAuditDto.Status.RED);
            dto.setStatusMessage("KRITISCH: " + String.join("; ", warnings));
        } else if (!warnings.isEmpty()) {
            dto.setOverallStatus(SecurityAuditDto.Status.YELLOW);
            dto.setStatusMessage("AUFFÄLLIGKEITEN: " + String.join("; ", warnings));
        } else {
            dto.setOverallStatus(SecurityAuditDto.Status.GREEN);
            dto.setStatusMessage("Alles in Ordnung — keine Auffälligkeiten erkannt.");
        }

        return dto;
    }

    // ---- Individual checks ----

    private void checkSshBruteForce(SecurityAuditDto dto) throws Exception {
        // Count failed password attempts
        String output = execCommand("grep", "-c", "Failed password", "/var/log/auth.log");
        try {
            dto.setFailedSshCount(Integer.parseInt(output.trim()));
        } catch (NumberFormatException e) {
            dto.setFailedSshCount(0);
        }

        // Top attacker IPs
        String topIpsOutput = execCommand("bash", "-c",
                "grep 'Failed password' /var/log/auth.log | awk '{print $(NF-3)}' | sort | uniq -c | sort -rn | head -15");
        List<IpCount> topIps = new ArrayList<>();
        for (String line : topIpsOutput.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+", 2);
            if (parts.length == 2) {
                try {
                    topIps.add(new IpCount(parts[1], Integer.parseInt(parts[0])));
                } catch (NumberFormatException ignored) {}
            }
        }
        dto.setTopAttackerIps(topIps);
    }

    private void checkFail2ban(SecurityAuditDto dto) throws Exception {
        String output = execCommand("bash", "-c", "sudo fail2ban-client status sshd 2>/dev/null || echo 'FAIL2BAN_NOT_AVAILABLE'");

        if (output.contains("FAIL2BAN_NOT_AVAILABLE")) {
            dto.setFail2banBannedCount(0);
            dto.setFail2banTotalBans(0);
            return;
        }

        // Parse "Currently banned:" line
        Pattern bannedPattern = Pattern.compile("Currently banned:\\s+(\\d+)");
        Matcher m = bannedPattern.matcher(output);
        if (m.find()) {
            dto.setFail2banBannedCount(Integer.parseInt(m.group(1)));
        }

        // Parse "Total banned:" line
        Pattern totalPattern = Pattern.compile("Total banned:\\s+(\\d+)");
        m = totalPattern.matcher(output);
        if (m.find()) {
            dto.setFail2banTotalBans(Integer.parseInt(m.group(1)));
        }

        // Parse banned IP list
        Pattern ipListPattern = Pattern.compile("Banned IP list:\\s+(.+)");
        m = ipListPattern.matcher(output);
        if (m.find()) {
            String ips = m.group(1).trim();
            if (!ips.isEmpty()) {
                dto.setFail2banBannedIps(Arrays.asList(ips.split("\\s+")));
            }
        }
    }

    private void checkSuspiciousNginxRequests(SecurityAuditDto dto) throws Exception {
        String output = execCommand("bash", "-c",
                "grep -iE '(union.*select|drop\\s+table|<script|\\.\\.\\./|etc/passwd|\\.php|wp-admin|wp-login|phpmyadmin)' /var/log/nginx/access.log 2>/dev/null | tail -50");

        List<String> suspicious = new ArrayList<>();
        int count = 0;
        for (String line : output.split("\n")) {
            line = line.trim();
            if (!line.isEmpty()) {
                count++;
                if (suspicious.size() < 50) {
                    // Truncate long lines
                    suspicious.add(line.length() > 200 ? line.substring(0, 200) + "..." : line);
                }
            }
        }
        dto.setSuspiciousRequestCount(count);
        dto.setSuspiciousRequests(suspicious);

        // Also count total matches (might exceed tail -50)
        try {
            String countOutput = execCommand("bash", "-c",
                    "grep -ciE '(union.*select|drop\\s+table|<script|\\.\\.\\./|etc/passwd|\\.php|wp-admin|wp-login|phpmyadmin)' /var/log/nginx/access.log 2>/dev/null || echo 0");
            dto.setSuspiciousRequestCount(Integer.parseInt(countOutput.trim()));
        } catch (NumberFormatException ignored) {}
    }

    private void checkTopRequestIps(SecurityAuditDto dto) throws Exception {
        String output = execCommand("bash", "-c",
                "awk '{print $1}' /var/log/nginx/access.log 2>/dev/null | sort | uniq -c | sort -rn | head -20");

        List<IpCount> topIps = new ArrayList<>();
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+", 2);
            if (parts.length == 2) {
                try {
                    topIps.add(new IpCount(parts[1], Integer.parseInt(parts[0])));
                } catch (NumberFormatException ignored) {}
            }
        }
        dto.setTopRequestIps(topIps);
    }

    // Known safe ports that should not trigger alerts
    private static final Set<Integer> KNOWN_SAFE_PORTS = Set.of(
            22,    // SSH
            80,    // HTTP (Nginx)
            443,   // HTTPS (Nginx)
            53,    // DNS (systemd-resolved) — localhost only
            631,   // CUPS (print service) — localhost only
            3002,  // Kursplaner App (Node.js)
            8080,  // WildFly HTTP
            8443,  // WildFly HTTPS
            9990,  // WildFly Management
            9999,  // WildFly remote debug
            5432   // PostgreSQL
    );

    private void checkOpenPorts(SecurityAuditDto dto) throws Exception {
        String output = execCommand("bash", "-c",
                "ss -tlnp 2>/dev/null | grep LISTEN || netstat -tlnp 2>/dev/null | grep LISTEN");

        List<String> openPorts = new ArrayList<>();
        List<String> unexpected = new ArrayList<>();

        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            openPorts.add(line);

            // Skip ports that only listen on localhost (127.x.x.x or [::1])
            if (line.contains("127.0.0.") || line.contains("[::1]")) {
                continue;
            }

            // Check if this line belongs to a Java/WildFly process (known internal ports)
            if (line.contains("java")) {
                continue;
            }

            // Extract port number from the local address
            Pattern portPattern = Pattern.compile(":([0-9]+)\\s");
            Matcher m = portPattern.matcher(line);
            while (m.find()) {
                try {
                    int port = Integer.parseInt(m.group(1));
                    // Skip known safe ports and ephemeral ports (>= 49152)
                    if (!KNOWN_SAFE_PORTS.contains(port) && port < 49152) {
                        unexpected.add(port + " (" + line.substring(0, Math.min(line.length(), 80)) + ")");
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        dto.setOpenPorts(openPorts);
        dto.setUnexpectedPorts(unexpected);
    }

    private void checkUfw(SecurityAuditDto dto) throws Exception {
        // Method 1: Read /etc/ufw/ufw.conf directly (no root needed)
        String confOutput = execCommand("bash", "-c",
                "cat /etc/ufw/ufw.conf 2>/dev/null | grep -i '^ENABLED' || echo 'CONF_NOT_FOUND'");
        boolean enabledViaConf = confOutput.toLowerCase().contains("enabled=yes");

        // Method 2: Try to get the full status output for display
        String statusOutput = execCommand("bash", "-c",
                "sudo ufw status verbose 2>/dev/null || ufw status verbose 2>/dev/null || echo 'Status konnte nicht abgefragt werden (benötigt root-Rechte)'");

        // Also check via iptables if ufw rules are loaded (fallback verification)
        if (!enabledViaConf) {
            String iptablesCheck = execCommand("bash", "-c",
                    "iptables -L -n 2>/dev/null | grep -c 'ufw' || echo '0'");
            try {
                int ufwRuleCount = Integer.parseInt(iptablesCheck.trim());
                if (ufwRuleCount > 0) {
                    enabledViaConf = true; // UFW rules are present in iptables
                }
            } catch (NumberFormatException ignored) {}
        }

        dto.setUfwActive(enabledViaConf || statusOutput.contains("Status: active") || statusOutput.contains("Status: aktiv"));

        // Build display string
        String displayRules = statusOutput;
        if (enabledViaConf && !statusOutput.contains("Status:")) {
            displayRules = "Status: active (ermittelt aus /etc/ufw/ufw.conf)\n\n" + statusOutput;
        }
        dto.setUfwRules(displayRules.length() > 2000 ? displayRules.substring(0, 2000) + "..." : displayRules);
    }

    private void checkRecentLogins(SecurityAuditDto dto) throws Exception {
        List<String> logins = new ArrayList<>();
        
        // 1. Web Admin Logins
        List<de.trademonitor.entity.LoginLog> webLogins = loginLogRepository.findAllByOrderByTimestampDesc()
                .stream().filter(de.trademonitor.entity.LoginLog::isSuccess).limit(20)
                .collect(java.util.stream.Collectors.toList());
        for (de.trademonitor.entity.LoginLog wl : webLogins) {
            String ts = wl.getTimestamp() != null ? wl.getTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "";
            logins.add(String.format("WEB-ADMIN %-10s %-16s %s", wl.getUsername(), wl.getIpAddress(), ts));
        }

        // 2. SSH Logins
        String output = execCommand("last", "-n", "20");
        for (String line : output.split("\n")) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("wtmp") && !line.startsWith("reboot")) {
                logins.add("SSH       " + line);
            }
        }
        
        dto.setRecentLogins(logins);
    }

    // ---- Fail2Ban Dynamic Methods ----

    public void syncFail2banWhitelist(String newIp) {
        if (newIp != null && !newIp.isBlank()) {
            globalConfigService.addFail2banWhitelistIp(newIp);
        }
        List<String> ips = globalConfigService.getFail2banWhitelistIps();
        String ipsString = String.join(" ", ips);
        
        try {
            // Use the wrapper script we created to bypass permission issues safely
            execCommand("bash", "-c", "sudo /usr/local/bin/update-fail2ban-whitelist.sh " + ipsString);
            if (newIp != null && !newIp.isBlank()) {
                execCommand("bash", "-c", "sudo fail2ban-client unban " + newIp + " --all");
            }
        } catch (Exception e) {
            System.err.println("[SecurityAudit] Failed to sync fail2ban whitelist: " + e.getMessage());
        }
    }

    public boolean unbanIp(String ipToUnban) {
        if (ipToUnban == null || ipToUnban.isBlank()) return false;
        try {
            String output = execCommand("bash", "-c", "sudo fail2ban-client unban " + ipToUnban.trim() + " --all");
            return !output.toLowerCase().contains("error");
        } catch (Exception e) {
            System.err.println("[SecurityAudit] Failed to unban IP " + ipToUnban + ": " + e.getMessage());
            return false;
        }
    }

    public java.util.List<java.util.Map<String, Object>> getFail2banLiveDetails() {
        java.util.List<java.util.Map<String, Object>> jailsData = new ArrayList<>();
        try {
            String status = execCommand("bash", "-c", "sudo fail2ban-client status 2>/dev/null || echo 'FAIL2BAN_NOT_AVAILABLE'");
            if (status.contains("FAIL2BAN_NOT_AVAILABLE")) return jailsData;

            Pattern p = Pattern.compile("Jail list:\\s+(.+)");
            Matcher m = p.matcher(status);
            if (m.find()) {
                String[] jails = m.group(1).split(",");
                for (String jail : jails) {
                    jail = jail.trim();
                    if (!jail.isEmpty()) {
                        java.util.Map<String, Object> jailInfo = new java.util.HashMap<>();
                        jailInfo.put("name", jail);
                        
                        String jailStatus = execCommand("bash", "-c", "sudo fail2ban-client status " + jail);
                        
                        jailInfo.put("currentlyFailed", extractRegex(jailStatus, "Currently failed:\\s+(\\d+)"));
                        jailInfo.put("totalFailed", extractRegex(jailStatus, "Total failed:\\s+(\\d+)"));
                        jailInfo.put("currentlyBanned", extractRegex(jailStatus, "Currently banned:\\s+(\\d+)"));
                        jailInfo.put("totalBanned", extractRegex(jailStatus, "Total banned:\\s+(\\d+)"));
                        
                        String bannedIps = extractRegex(jailStatus, "Banned IP list:\\s+(.*)");
                        if (bannedIps.trim().isEmpty() || bannedIps.equals("0")) {
                            jailInfo.put("bannedIpList", new ArrayList<String>());
                        } else {
                            jailInfo.put("bannedIpList", Arrays.asList(bannedIps.trim().split("\\s+")));
                        }
                        jailsData.add(jailInfo);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing fail2ban details: " + e.getMessage());
        }
        return jailsData;
    }

    private String extractRegex(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find() && matcher.groupCount() > 0) {
            String res = matcher.group(1);
            return res != null ? res : "0";
        }
        return "0";
    }

    // ---- Utility methods ----

    private String execCommand(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        process.waitFor();
        return output.toString();
    }

    private void saveAudit(SecurityAuditDto dto) {
        try {
            Files.createDirectories(Paths.get(AUDIT_DIR));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(AUDIT_FILE), dto);
            System.out.println("[SecurityAudit] Saved to " + AUDIT_FILE);
        } catch (Exception e) {
            System.err.println("[SecurityAudit] Error saving audit: " + e.getMessage());
        }
    }

    /**
     * Send email and dashboard notification if security audit found issues.
     */
    private void sendAuditAlertIfNeeded(SecurityAuditDto result) {
        if (result.getOverallStatus() == SecurityAuditDto.Status.GREEN) {
            return; // All good, no alert needed
        }

        boolean isCritical = result.getOverallStatus() == SecurityAuditDto.Status.RED;
        String statusLabel = isCritical ? "KRITISCH" : "WARNUNG";

        // 1. Create dashboard notification
        notificationService.addNotification(new AdminNotification(
                Category.SECURITY,
                isCritical ? Severity.CRITICAL : Severity.WARNING,
                "🛡️ Security Audit: " + statusLabel,
                result.getStatusMessage()));

        // 2. Send email alert
        StringBuilder body = new StringBuilder();
        body.append("🛡️ Security Audit Alert — Trade Monitor\n");
        body.append("═══════════════════════════════════════\n\n");
        body.append("Status: ").append(statusLabel).append("\n");
        body.append("Zeit: ").append(result.getCheckTime()).append("\n\n");
        body.append("Zusammenfassung:\n").append(result.getStatusMessage()).append("\n\n");

        // Add details
        body.append("─────────────────────────────────────\n");
        body.append("Details:\n");
        body.append("  • Fehlgeschlagene SSH-Logins: ").append(result.getFailedSshCount()).append("\n");
        body.append("  • Fail2Ban aktuell gebannt: ").append(result.getFail2banBannedCount()).append("\n");
        body.append("  • Verdächtige Web-Requests: ").append(result.getSuspiciousRequestCount()).append("\n");
        body.append("  • Unerwartete Ports: ").append(result.getUnexpectedPorts().size()).append("\n");
        body.append("  • UFW Firewall: ").append(result.isUfwActive() ? "Aktiv" : "INAKTIV!").append("\n");

        if (!result.getTopAttackerIps().isEmpty()) {
            body.append("\nTop Angreifer-IPs:\n");
            for (IpCount ip : result.getTopAttackerIps().subList(0, Math.min(5, result.getTopAttackerIps().size()))) {
                body.append("  • ").append(ip.getIp()).append(" → ").append(ip.getCount()).append(" Versuche\n");
            }
        }

        body.append("\n─────────────────────────────────────\n");
        body.append("Details ansehen: /admin/security-audit\n");

        try {
            emailService.sendSyncWarningEmail(
                    "🛡️ Security Alert: " + statusLabel + " — Trade Monitor",
                    body.toString());
        } catch (Exception e) {
            System.err.println("[SecurityAudit] Failed to send alert email: " + e.getMessage());
        }

        // Trigger Homey Siren if enabled for security alerts
        if (globalConfigService.isHomeyTriggerSecurity()) {
            System.out.println("[SecurityAudit] Triggering Homey siren for security alert.");
            homeyService.triggerSiren();
        }
    }
}
