package de.trademonitor.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class HomeyService {

    private static final Logger logger = LoggerFactory.getLogger(HomeyService.class);

    private final GlobalConfigService globalConfigService;
    private final RestTemplate restTemplate;

    public HomeyService(GlobalConfigService globalConfigService) {
        this.globalConfigService = globalConfigService;
        this.restTemplate = new RestTemplate();
    }

    public void triggerSiren() {
        String homeyId = globalConfigService.getHomeyId();
        String eventName = globalConfigService.getHomeyEvent();
        int repeatCount = globalConfigService.getHomeyRepeatCount();

        if (homeyId == null || homeyId.isEmpty()) {
            logger.warn("Homey ID is not configured. Cannot trigger siren.");
            return;
        }

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
