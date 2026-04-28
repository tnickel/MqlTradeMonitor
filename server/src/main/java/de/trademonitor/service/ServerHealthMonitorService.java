package de.trademonitor.service;

import de.trademonitor.dto.AdminNotification;
import de.trademonitor.dto.AdminNotification.Category;
import de.trademonitor.dto.AdminNotification.Severity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled service that monitors server health metrics every 30 minutes.
 * Sends email alerts and creates dashboard notifications when thresholds are exceeded.
 */
@Service
public class ServerHealthMonitorService {

    private static final double DISK_THRESHOLD_PERCENT = 90.0;
    private static final double RAM_THRESHOLD_PERCENT = 90.0;
    private static final double CPU_THRESHOLD_PERCENT = 95.0;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AdminNotificationService notificationService;

    @Autowired
    private HomeyService homeyService;

    @Autowired
    private GlobalConfigService globalConfigService;

    private LocalDateTime lastCheckTime;
    private String lastStatus = "OK";
    private List<String> lastProblems = new ArrayList<>();

    /**
     * Runs every 30 minutes to check server health.
     */
    @Scheduled(fixedRate = 1800000, initialDelay = 60000) // 30 min, 1 min initial delay
    public void scheduledHealthCheck() {
        System.out.println("[HealthMonitor] Running scheduled health check...");
        runHealthCheck();
    }

    /**
     * Run health check and alert if problems are found.
     */
    public void runHealthCheck() {
        lastCheckTime = LocalDateTime.now();
        lastProblems.clear();

        // 1. Check Disk Usage
        checkDiskUsage();

        // 2. Check System RAM
        checkSystemRam();

        // 3. Check CPU Load
        checkCpuLoad();

        // Update status
        if (lastProblems.isEmpty()) {
            lastStatus = "OK";
            System.out.println("[HealthMonitor] All checks passed.");
            homeyService.setAlarmState("HEALTH", false);
        } else {
            lastStatus = "PROBLEM";
            System.out.println("[HealthMonitor] Problems detected: " + lastProblems.size());
            sendHealthAlert();
        }
    }

    private void checkDiskUsage() {
        try {
            File root = new File("/");
            if (!root.exists()) {
                root = new File(".");
            }
            long totalSpace = root.getTotalSpace();
            long freeSpace = root.getUsableSpace();

            if (totalSpace > 0) {
                double usedPercent = ((double) (totalSpace - freeSpace) / totalSpace) * 100.0;
                if (usedPercent >= DISK_THRESHOLD_PERCENT) {
                    String msg = String.format("Disk-Auslastung bei %.1f%% (Frei: %s von %s)",
                            usedPercent, formatSize(freeSpace), formatSize(totalSpace));
                    lastProblems.add(msg);
                    notificationService.addNotification(new AdminNotification(
                            Category.HEALTH,
                            usedPercent >= 95.0 ? Severity.CRITICAL : Severity.WARNING,
                            "💾 Hohe Disk-Auslastung",
                            msg));
                }
            }
        } catch (Exception e) {
            System.err.println("[HealthMonitor] Disk check error: " + e.getMessage());
        }
    }

    private void checkSystemRam() {
        try {
            javax.management.MBeanServer mbs = java.lang.management.ManagementFactory.getPlatformMBeanServer();
            javax.management.ObjectName name = javax.management.ObjectName.getInstance("java.lang:type=OperatingSystem");
            long sysTotal = (Long) mbs.getAttribute(name, "TotalPhysicalMemorySize");
            long sysFree = (Long) mbs.getAttribute(name, "FreePhysicalMemorySize");

            if (sysTotal > 0) {
                double usedPercent = ((double) (sysTotal - sysFree) / sysTotal) * 100.0;
                if (usedPercent >= RAM_THRESHOLD_PERCENT) {
                    String msg = String.format("RAM-Auslastung bei %.1f%% (Frei: %s von %s)",
                            usedPercent, formatSize(sysFree), formatSize(sysTotal));
                    lastProblems.add(msg);
                    notificationService.addNotification(new AdminNotification(
                            Category.HEALTH,
                            usedPercent >= 95.0 ? Severity.CRITICAL : Severity.WARNING,
                            "🧠 Hohe RAM-Auslastung",
                            msg));
                }
            }
        } catch (Throwable t) {
            System.err.println("[HealthMonitor] RAM check error: " + t.getMessage());
        }
    }

    private void checkCpuLoad() {
        try {
            javax.management.MBeanServer mbs = java.lang.management.ManagementFactory.getPlatformMBeanServer();
            javax.management.ObjectName name = javax.management.ObjectName.getInstance("java.lang:type=OperatingSystem");
            double load = -1.0;
            try {
                load = (Double) mbs.getAttribute(name, "CpuLoad");
            } catch (Exception e) {
                try {
                    load = (Double) mbs.getAttribute(name, "SystemCpuLoad");
                } catch (Exception ex) {
                    load = (Double) mbs.getAttribute(name, "ProcessCpuLoad");
                }
            }

            if (load >= 0) {
                double cpuPercent = load * 100.0;
                if (cpuPercent >= CPU_THRESHOLD_PERCENT) {
                    String msg = String.format("CPU-Auslastung bei %.1f%%", cpuPercent);
                    lastProblems.add(msg);
                    notificationService.addNotification(new AdminNotification(
                            Category.HEALTH,
                            Severity.CRITICAL,
                            "🔥 Hohe CPU-Auslastung",
                            msg));
                }
            }
        } catch (Throwable t) {
            System.err.println("[HealthMonitor] CPU check error: " + t.getMessage());
        }
    }

    private void sendHealthAlert() {
        StringBuilder body = new StringBuilder();
        body.append("⚠️ Server Health Alert — Trade Monitor\n");
        body.append("═══════════════════════════════════════\n\n");
        body.append("Zeit: ").append(lastCheckTime).append("\n\n");
        body.append("Folgende Probleme wurden erkannt:\n\n");
        for (int i = 0; i < lastProblems.size(); i++) {
            body.append("  ").append(i + 1).append(". ").append(lastProblems.get(i)).append("\n");
        }
        body.append("\n─────────────────────────────────────\n");
        body.append("Bitte prüfe den Server unter: /admin/health\n");

        try {
            emailService.sendSyncWarningEmail("⚠️ Server Health Alert", body.toString());
        } catch (Exception e) {
            System.err.println("[HealthMonitor] Failed to send health alert email: " + e.getMessage());
        }

        // Trigger Homey Siren if enabled for health alerts
        if (globalConfigService.isHomeyTriggerHealth()) {
            System.out.println("[HealthMonitor] Triggering Homey siren for health alert.");
            homeyService.setAlarmState("HEALTH", true);
        }
    }

    // Getters for status display
    public LocalDateTime getLastCheckTime() { return lastCheckTime; }
    public String getLastStatus() { return lastStatus; }
    public List<String> getLastProblems() { return new ArrayList<>(lastProblems); }

    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format(java.util.Locale.US, "%.1f %sB", (double) v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}
