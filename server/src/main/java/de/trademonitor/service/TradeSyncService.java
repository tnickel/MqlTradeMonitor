package de.trademonitor.service;

import de.trademonitor.model.Account;
import de.trademonitor.model.Trade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class TradeSyncService {

    @Autowired
    private AccountManager accountManager;

    @Autowired
    private GlobalConfigService globalConfigService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private HomeyService homeyService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");

    private long lastCheckTime = 0;
    private boolean warningEmailSent = false;

    // Metrics
    private String lastSyncStatus = "OK";
    private int lastCheckedTradeCount = 0;

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("lastCheckTime", lastCheckTime);
        int interval = globalConfigService.getTradeSyncIntervalSeconds();
        metrics.put("nextCheckTime", lastCheckTime + (interval * 1000L));
        metrics.put("status", lastSyncStatus);
        metrics.put("checkedCount", lastCheckedTradeCount);
        metrics.put("interval", interval);
        return metrics;
    }

    @Scheduled(fixedRate = 1000) // Poll every second
    public void checkTradeSync() {
        int intervalSeconds = globalConfigService.getTradeSyncIntervalSeconds();
        if (intervalSeconds <= 0)
            return; // Disabled

        long now = System.currentTimeMillis();
        // Use greater-equal
        if (now - lastCheckTime < intervalSeconds * 1000L) {
            return; // Interval not reached
        }
        lastCheckTime = now;

        performSyncCheck();
    }

    private void performSyncCheck() {
        // 1. Collect all Open Trades from DEMO accounts
        List<Trade> demoTrades = new ArrayList<>();
        for (Account account : accountManager.getAllAccounts()) {
            if ("DEMO".equalsIgnoreCase(account.getType())) {
                demoTrades.addAll(account.getOpenTrades());
            }
        }

        // 2. Iterate through REAL accounts
        boolean globalWarning = false;
        int totalRealTrades = 0;

        for (Account account : accountManager.getAllAccounts()) {
            if ("REAL".equalsIgnoreCase(account.getType())) {
                boolean accountHasWarning = false;
                Map<Long, String> newStatuses = new HashMap<>();

                List<Trade> realTrades = account.getOpenTrades();
                totalRealTrades += realTrades.size();

                for (Trade realTrade : realTrades) {
                    boolean matched = findMatchingTrade(realTrade, demoTrades);

                    if (matched) {
                        newStatuses.put(realTrade.getTicket(), "MATCHED");
                    } else {
                        newStatuses.put(realTrade.getTicket(), "WARNING");
                        accountHasWarning = true;
                    }
                }

                // Push statuses to account (persists them and updates current trades)
                account.updateSyncStatuses(newStatuses);
                account.setSyncWarning(accountHasWarning);

                if (accountHasWarning) {
                    globalWarning = true;
                }

                if (newStatuses.size() > 0) {
                    // Debug only occasionally or loop could be noisy
                }
            } else {
                // Reset for non-real accounts just in case
                account.setSyncWarning(false);
            }
        }

        lastCheckedTradeCount = totalRealTrades;

        if (globalWarning) {
            lastSyncStatus = "WARNING";
            if (!warningEmailSent) {
                emailService.sendSyncWarningEmail("Trade Monitor Warnung",
                        "Achtung: Es wurden nicht-synchronisierte Trades auf Real-Konten gefunden! Bitte Dashboard prüfen.");

                // Trigger Homey Siren if enabled
                if (globalConfigService.isHomeyTriggerSync()) {
                    homeyService.triggerSiren();
                }

                warningEmailSent = true;
            }
        } else {
            lastSyncStatus = "OK";
            warningEmailSent = false; // Reset latch when clear
        }
    }

    private boolean findMatchingTrade(Trade realTrade, List<Trade> demoTrades) {
        for (Trade demoTrade : demoTrades) {
            // Criteria 1: Symbol must match
            if (!realTrade.getSymbol().equals(demoTrade.getSymbol()))
                continue;

            // Criteria 2: Type must match
            if (!realTrade.getType().equals(demoTrade.getType()))
                continue;

            // Criteria 3: Open Time within tolerance (e.g. 60 seconds)
            if (isTimeMatch(realTrade.getOpenTime(), demoTrade.getOpenTime())) {
                return true;
            }
        }
        return false;
    }

    private boolean isTimeMatch(String time1Str, String time2Str) {
        try {
            LocalDateTime t1 = LocalDateTime.parse(time1Str, TIME_FORMATTER);
            LocalDateTime t2 = LocalDateTime.parse(time2Str, TIME_FORMATTER);

            long diff = Math.abs(ChronoUnit.SECONDS.between(t1, t2));
            return diff <= 60; // 1 minute tolerance
        } catch (Exception e) {
            // If parsing fails, fall back to loose string matching or assume no match
            return false;
        }
    }
}
