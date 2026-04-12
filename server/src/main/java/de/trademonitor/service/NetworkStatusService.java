package de.trademonitor.service;

import de.trademonitor.entity.NetworkStatusLogEntity;
import de.trademonitor.repository.NetworkStatusLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class NetworkStatusService {

    @Autowired
    private GlobalConfigService globalConfigService;

    @Autowired
    private NetworkStatusLogRepository logRepository;

    private AtomicReference<LocalDateTime> lastGlobalHeartbeat = new AtomicReference<>();

    public void registerHeartbeat() {
        lastGlobalHeartbeat.set(LocalDateTime.now());
    }

    @Scheduled(fixedDelay = 60000) // Every minute
    public void checkNetworkStatus() {
        String newStatus = "ONLINE";

        // 1. Check Maintenance Mode via .war file
        if (isWarFileModifiedRecently()) {
            newStatus = "MAINTENANCE";
        } else {
            // 2. Not in maintenance, check heartbeat
            LocalDateTime lastMb = lastGlobalHeartbeat.get();
            if (lastMb == null) {
                // If the app just started and no heartbeat received yet, give it some grace period.
                // We assume ONLINE until proven OFFLINE. But if it's been active for a while...
                // Actually, if lastMb is null, it might be an outage right after boot.
                // Let's set the boot time as the initial heartbeat so it goes offline eventually if strictly nothing comes.
                lastGlobalHeartbeat.compareAndSet(null, LocalDateTime.now());
            } else {
                long thresholdMins = globalConfigService.getNetworkOfflineThresholdMins();
                if (ChronoUnit.MINUTES.between(lastMb, LocalDateTime.now()) >= thresholdMins) {
                    newStatus = "OFFLINE";
                }
            }
        }

        // 3. Update State in DB
        NetworkStatusLogEntity currentLog = logRepository.findFirstByOrderByStartTimeDesc();

        if (currentLog == null) {
            // First time ever
            logRepository.save(new NetworkStatusLogEntity(LocalDateTime.now(), newStatus));
        } else if (!currentLog.getStatus().equals(newStatus)) {
            // State changed
            currentLog.setEndTime(LocalDateTime.now());
            logRepository.save(currentLog);
            logRepository.save(new NetworkStatusLogEntity(LocalDateTime.now(), newStatus));
            System.out.println("[NetworkStatusService] Status changed from " + currentLog.getStatus() + " to " + newStatus);
        }
    }

    public String getCurrentStatus() {
        NetworkStatusLogEntity currentLog = logRepository.findFirstByOrderByStartTimeDesc();
        return currentLog != null ? currentLog.getStatus() : "ONLINE";
    }

    private boolean isWarFileModifiedRecently() {
        File warFile = getWarFile();
        if (warFile != null && warFile.exists()) {
            long fileTimeMillis = 0;

            // WildFly creates a .deployed marker file when it finishes deploying.
            // This file is never touched again, making it immune to whatever is touching the .war file.
            File deployedMarker = new File(warFile.getAbsolutePath() + ".deployed");
            if (deployedMarker.exists()) {
                fileTimeMillis = deployedMarker.lastModified();
            } else {
                // Determine write time of the WAR file
                try {
                    java.nio.file.attribute.BasicFileAttributes attr = java.nio.file.Files.readAttributes(warFile.toPath(), java.nio.file.attribute.BasicFileAttributes.class);
                    fileTimeMillis = attr.creationTime().toMillis();
                    // On some Linux systems creationTime might equal lastModified if not supported.
                } catch (Exception e) {
                    fileTimeMillis = warFile.lastModified();
                }
            }

            long now = System.currentTimeMillis();
            long diffMins = (now - fileTimeMillis) / (1000 * 60);

            return diffMins < globalConfigService.getMaintenanceTimeoutMins();
        }
        return false;
    }

    private File getWarFile() {
        // Try Hardcoded Contabo production path first
        File prodWar = new File("/opt/wildfly/standalone/deployments/ROOT.war");
        if (prodWar.exists()) {
            return prodWar;
        }

        // Try WildFly Server path
        String baseDir = System.getProperty("jboss.server.base.dir");
        if (baseDir != null) {
            File wildflyWar = new File(baseDir, "deployments/ROOT.war");
            if (wildflyWar.exists()) {
                return wildflyWar;
            }
        }
        // Fallback for local development
        File localWar = new File("target/ROOT.war");
        if (localWar.exists()) {
            return localWar;
        }
        return null;
    }
}
