package de.trademonitor.security;

import de.trademonitor.service.GlobalConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force login protection service.
 * Tracks failed login attempts per IP address and blocks further attempts
 * after the configurable threshold is exceeded for a configurable lockout
 * duration.
 */
@Service
public class BruteForceProtectionService {

    @Autowired
    private GlobalConfigService configService;

    // IP -> FailureRecord(count, firstFailureTime)
    private final ConcurrentHashMap<String, FailureRecord> failureMap = new ConcurrentHashMap<>();

    private static class FailureRecord {
        int count;
        LocalDateTime firstFailure;
        LocalDateTime lockoutUntil;

        FailureRecord() {
            this.count = 1;
            this.firstFailure = LocalDateTime.now();
            this.lockoutUntil = null;
        }
    }

    /**
     * Record a failed login attempt for the given IP.
     */
    public void recordFailure(String ip) {
        if (!configService.isSecBruteForceEnabled())
            return;

        int maxAttempts = configService.getSecBruteForceMaxAttempts();
        int lockoutMins = configService.getSecBruteForceLockoutMins();

        failureMap.compute(ip, (key, existing) -> {
            if (existing == null) {
                return new FailureRecord();
            }
            // If lockout has expired, reset
            if (existing.lockoutUntil != null && LocalDateTime.now().isAfter(existing.lockoutUntil)) {
                return new FailureRecord();
            }
            existing.count++;
            if (existing.count >= maxAttempts) {
                existing.lockoutUntil = LocalDateTime.now().plusMinutes(lockoutMins);
            }
            return existing;
        });
    }

    /**
     * Record a successful login for the given IP (clears failure counter).
     */
    public void recordSuccess(String ip) {
        failureMap.remove(ip);
    }

    /**
     * Check if the given IP is currently locked out.
     */
    public boolean isBlocked(String ip) {
        if (!configService.isSecBruteForceEnabled())
            return false;

        FailureRecord record = failureMap.get(ip);
        if (record == null)
            return false;

        if (record.lockoutUntil != null) {
            if (LocalDateTime.now().isBefore(record.lockoutUntil)) {
                return true;
            } else {
                // Lockout expired, clean up
                failureMap.remove(ip);
                return false;
            }
        }
        return false;
    }

    /**
     * Get remaining lockout seconds for a given IP (for display purposes).
     */
    public long getRemainingLockoutSeconds(String ip) {
        FailureRecord record = failureMap.get(ip);
        if (record == null || record.lockoutUntil == null)
            return 0;
        long seconds = java.time.Duration.between(LocalDateTime.now(), record.lockoutUntil).getSeconds();
        return Math.max(0, seconds);
    }

    /**
     * Cleanup expired entries. Called periodically.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 600000) // every 10 minutes
    public void cleanupExpired() {
        failureMap.entrySet().removeIf(entry -> {
            FailureRecord r = entry.getValue();
            if (r.lockoutUntil != null && LocalDateTime.now().isAfter(r.lockoutUntil)) {
                return true;
            }
            // Also clean entries older than 30 minutes without lockout
            if (r.lockoutUntil == null && r.firstFailure.plusMinutes(30).isBefore(LocalDateTime.now())) {
                return true;
            }
            return false;
        });
    }
}
