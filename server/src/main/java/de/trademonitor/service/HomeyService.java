package de.trademonitor.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HomeyService {

    private static final Logger logger = LoggerFactory.getLogger(HomeyService.class);

    private final GlobalConfigService globalConfigService;
    private final RestTemplate restTemplate;
    
    private final Set<String> activeAlarms = ConcurrentHashMap.newKeySet();
    private long lastSirenTime = 0;

    public HomeyService(GlobalConfigService globalConfigService) {
        this.globalConfigService = globalConfigService;
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * Sets or clears an alarm state for a specific source.
     * @param alarmId The unique identifier of the alarm source (e.g., "COPIER_SYNC", "HEALTH")
     * @param isActive True if the alarm is currently active, false if cleared.
     */
    public void setAlarmState(String alarmId, boolean isActive) {
        if (isActive) {
            if (activeAlarms.add(alarmId)) {
                logger.info("[HomeyService] Alarm activated: {}", alarmId);
                // Trigger immediately upon first activation if interval has passed
                long repeatMs = globalConfigService.getHomeyRepeatIntervalMins() * 60L * 1000L;
                if (System.currentTimeMillis() - lastSirenTime >= repeatMs) {
                    triggerSiren();
                }
            }
        } else {
            if (activeAlarms.remove(alarmId)) {
                logger.info("[HomeyService] Alarm cleared: {}", alarmId);
            }
        }
    }
    
    /**
     * Checks every minute if there are active alarms and if the repeat interval has passed.
     */
    @Scheduled(fixedRate = 60000)
    public void checkAndRepeatSiren() {
        if (activeAlarms.isEmpty()) {
            return;
        }
        
        long repeatMs = globalConfigService.getHomeyRepeatIntervalMins() * 60L * 1000L;
        if (System.currentTimeMillis() - lastSirenTime >= repeatMs) {
            logger.info("[HomeyService] Repeating siren due to active alarms: {}", activeAlarms);
            triggerSiren();
        }
    }

    /**
     * Triggers the siren immediately.
     */
    public void triggerSiren() {
        String homeyId = globalConfigService.getHomeyId();
        String eventName = globalConfigService.getHomeyEvent();
        int repeatCount = globalConfigService.getHomeyRepeatCount();

        if (homeyId == null || homeyId.isEmpty()) {
            logger.warn("Homey ID is not configured. Cannot trigger siren.");
            return;
        }

        // Record the time of this trigger
        lastSirenTime = System.currentTimeMillis();

        // Construct URL: https://webhook.homey.app/<HOMEY-ID>/<EVENT>
        String url = String.format("https://webhook.homey.app/%s/%s", homeyId, eventName);

        // Run in a separate thread to avoid blocking the caller
        new Thread(() -> {
            for (int i = 0; i < repeatCount; i++) {
                try {
                    logger.info("Triggering Homey Siren ({}/{}) via Webhook: {}", (i + 1), repeatCount, url);
                    restTemplate.getForObject(url, String.class);
                    logger.info("Homey Siren triggered successfully.");

                    if (i < repeatCount - 1) {
                        Thread.sleep(5000); // 5 seconds delay
                    }
                } catch (Exception e) {
                    logger.error("Failed to trigger Homey Siren: {}", e.getMessage());
                }
            }
        }).start();
    }
}
