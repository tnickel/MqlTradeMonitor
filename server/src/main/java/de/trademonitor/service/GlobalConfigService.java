package de.trademonitor.service;

import de.trademonitor.entity.GlobalConfigEntity;
import de.trademonitor.repository.GlobalConfigRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GlobalConfigService {

    // KEY_MAGIC_MAX_AGE removed — now stored per-account in AccountEntity

    @Autowired
    private GlobalConfigRepository repository;

    public static final String KEY_TRADE_SYNC_INTERVAL = "TRADE_SYNC_INTERVAL_SECONDS";

    // Live Indicator Config Keys
    public static final String KEY_LIVE_GREEN_MINS = "LIVE_GREEN_MINS";
    public static final String KEY_LIVE_YELLOW_MINS = "LIVE_YELLOW_MINS";
    public static final String KEY_LIVE_ORANGE_MINS = "LIVE_ORANGE_MINS";
    public static final String KEY_LIVE_COLOR_GREEN = "LIVE_COLOR_GREEN";
    public static final String KEY_LIVE_COLOR_YELLOW = "LIVE_COLOR_YELLOW";
    public static final String KEY_LIVE_COLOR_ORANGE = "LIVE_COLOR_ORANGE";
    public static final String KEY_LIVE_COLOR_RED = "LIVE_COLOR_RED";

    // Mail Config Keys
    public static final String KEY_MAIL_HOST = "MAIL_HOST";
    public static final String KEY_MAIL_PORT = "MAIL_PORT";
    public static final String KEY_MAIL_USER = "MAIL_USER";
    public static final String KEY_MAIL_PASSWORD = "MAIL_PASSWORD";
    public static final String KEY_MAIL_FROM = "MAIL_FROM";
    public static final String KEY_MAIL_TO = "MAIL_TO";
    public static final String KEY_MAIL_MAX_PER_DAY = "MAIL_MAX_PER_DAY";

    // Log Retention Keys
    public static final String KEY_LOG_LOGIN_DAYS = "LOG_LOGIN_DAYS";
    public static final String KEY_LOG_CONN_DAYS = "LOG_CONN_DAYS";
    public static final String KEY_LOG_CLIENT_DAYS = "LOG_CLIENT_DAYS";
    public static final String KEY_LOG_EA_DAYS = "LOG_EA_DAYS";

    // Cache the value to avoid hitting DB on every request
    // cachedMaxAgeDays removed — now stored per-account in AccountEntity
    private int cachedSyncIntervalSeconds = 60; // Default 60 seconds

    private int cachedLiveGreenMins = 1;
    private int cachedLiveYellowMins = 5;
    private int cachedLiveOrangeMins = 60;
    private String cachedLiveColorGreen = "#10b981";
    private String cachedLiveColorYellow = "#f59e0b"; // Updated to lighter yellow
    private String cachedLiveColorOrange = "#f97316"; // Orange
    private String cachedLiveColorRed = "#ef4444";

    private int cachedLogLoginDays = 360;
    private int cachedLogConnDays = 3;
    private int cachedLogClientDays = 3;
    private int cachedLogEaDays = 30;

    // Security Config Keys
    public static final String KEY_SEC_RATE_LIMIT_ENABLED = "SEC_RATE_LIMIT_ENABLED";
    public static final String KEY_SEC_RATE_LIMIT_PER_MIN = "SEC_RATE_LIMIT_PER_MIN";
    public static final String KEY_SEC_BRUTE_FORCE_ENABLED = "SEC_BRUTE_FORCE_ENABLED";
    public static final String KEY_SEC_BRUTE_FORCE_MAX_ATTEMPTS = "SEC_BRUTE_FORCE_MAX_ATTEMPTS";
    public static final String KEY_SEC_BRUTE_FORCE_LOCKOUT_MINS = "SEC_BRUTE_FORCE_LOCKOUT_MINS";
    public static final String KEY_SEC_HEADERS_ENABLED = "SEC_HEADERS_ENABLED";
    public static final String KEY_SEC_MAX_SESSIONS = "SEC_MAX_SESSIONS";
    public static final String KEY_SEC_H2_CONSOLE_ENABLED = "SEC_H2_CONSOLE_ENABLED";

    private boolean cachedSecRateLimitEnabled = true;
    private int cachedSecRateLimitPerMin = 100;
    private boolean cachedSecBruteForceEnabled = true;
    private int cachedSecBruteForceMaxAttempts = 5;
    private int cachedSecBruteForceLockoutMins = 15;
    private boolean cachedSecHeadersEnabled = true;
    private int cachedSecMaxSessions = 3;
    private boolean cachedSecH2ConsoleEnabled = false;

    // Broker Commission Factor
    public static final String KEY_BROKER_COMM_FACTOR_PREFIX = "BROKER_COMM_FACTOR.";
    private Map<String, Double> cachedBrokerCommFactors = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        // Load on startup
        // Magic max age loading removed — now per-account

        repository.findById(KEY_TRADE_SYNC_INTERVAL).ifPresent(entity -> {
            try {
                cachedSyncIntervalSeconds = Integer.parseInt(entity.getConfValue());
            } catch (NumberFormatException e) {
                System.err.println(
                        "Invalid number format for config " + KEY_TRADE_SYNC_INTERVAL + ": " + entity.getConfValue());
            }
        });

        repository.findById(KEY_LIVE_GREEN_MINS).ifPresent(entity -> {
            try {
                cachedLiveGreenMins = Integer.parseInt(entity.getConfValue());
            } catch (NumberFormatException e) {
            }
        });

        repository.findById(KEY_LIVE_YELLOW_MINS).ifPresent(entity -> {
            try {
                cachedLiveYellowMins = Integer.parseInt(entity.getConfValue());
            } catch (NumberFormatException e) {
            }
        });

        repository.findById(KEY_LIVE_ORANGE_MINS).ifPresent(entity -> {
            try {
                cachedLiveOrangeMins = Integer.parseInt(entity.getConfValue());
            } catch (NumberFormatException e) {
            }
        });

        repository.findById(KEY_LIVE_COLOR_GREEN).ifPresent(entity -> cachedLiveColorGreen = entity.getConfValue());
        repository.findById(KEY_LIVE_COLOR_YELLOW).ifPresent(entity -> cachedLiveColorYellow = entity.getConfValue());
        repository.findById(KEY_LIVE_COLOR_ORANGE).ifPresent(entity -> cachedLiveColorOrange = entity.getConfValue());
        repository.findById(KEY_LIVE_COLOR_RED).ifPresent(entity -> cachedLiveColorRed = entity.getConfValue());

        repository.findById(KEY_LOG_LOGIN_DAYS).ifPresent(entity -> {
            try {
                cachedLogLoginDays = Integer.parseInt(entity.getConfValue());
            } catch (NumberFormatException e) {
            }
        });
        repository.findById(KEY_LOG_CONN_DAYS).ifPresent(entity -> {
            try {
                cachedLogConnDays = Integer.parseInt(entity.getConfValue());
            } catch (NumberFormatException e) {
            }
        });
        repository.findById(KEY_LOG_CLIENT_DAYS).ifPresent(entity -> {
            try {
                cachedLogClientDays = Integer.parseInt(entity.getConfValue());
            } catch (NumberFormatException e) {
            }
        });
        repository.findById(KEY_LOG_EA_DAYS).ifPresent(entity -> {
            try {
                cachedLogEaDays = Integer.parseInt(entity.getConfValue());
            } catch (NumberFormatException e) {
            }
        });

        repository.findById(KEY_SYNC_ALARM_DELAY_MINS).ifPresent(entity -> {
            try {
                cachedSyncAlarmDelayMins = Integer.parseInt(entity.getConfValue());
            } catch (NumberFormatException e) {
            }
        });

        // Load Security Config
        repository.findById(KEY_SEC_RATE_LIMIT_ENABLED)
                .ifPresent(e -> cachedSecRateLimitEnabled = Boolean.parseBoolean(e.getConfValue()));
        repository.findById(KEY_SEC_RATE_LIMIT_PER_MIN).ifPresent(e -> {
            try {
                cachedSecRateLimitPerMin = Integer.parseInt(e.getConfValue());
            } catch (NumberFormatException ex) {
            }
        });
        repository.findById(KEY_SEC_BRUTE_FORCE_ENABLED)
                .ifPresent(e -> cachedSecBruteForceEnabled = Boolean.parseBoolean(e.getConfValue()));
        repository.findById(KEY_SEC_BRUTE_FORCE_MAX_ATTEMPTS).ifPresent(e -> {
            try {
                cachedSecBruteForceMaxAttempts = Integer.parseInt(e.getConfValue());
            } catch (NumberFormatException ex) {
            }
        });
        repository.findById(KEY_SEC_BRUTE_FORCE_LOCKOUT_MINS).ifPresent(e -> {
            try {
                cachedSecBruteForceLockoutMins = Integer.parseInt(e.getConfValue());
            } catch (NumberFormatException ex) {
            }
        });
        repository.findById(KEY_SEC_HEADERS_ENABLED)
                .ifPresent(e -> cachedSecHeadersEnabled = Boolean.parseBoolean(e.getConfValue()));
        repository.findById(KEY_SEC_MAX_SESSIONS).ifPresent(e -> {
            try {
                cachedSecMaxSessions = Integer.parseInt(e.getConfValue());
            } catch (NumberFormatException ex) {
            }
        });
        repository.findById(KEY_SEC_H2_CONSOLE_ENABLED)
                .ifPresent(e -> cachedSecH2ConsoleEnabled = Boolean.parseBoolean(e.getConfValue()));

        // Load Broker Commission Factors
        repository.findAll().forEach(entity -> {
            if (entity.getConfKey().startsWith(KEY_BROKER_COMM_FACTOR_PREFIX)) {
                String brokerName = entity.getConfKey().substring(KEY_BROKER_COMM_FACTOR_PREFIX.length());
                try {
                    cachedBrokerCommFactors.put(brokerName, Double.parseDouble(entity.getConfValue()));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        // Pre-populate Tickmill Ltd with factor 2.0 if not already configured
        if (!cachedBrokerCommFactors.containsKey("Tickmill Ltd")) {
            saveBrokerCommFactor("Tickmill Ltd", 2.0);
        }

        // Load Copier Verification Config
        repository.findById(KEY_COPIER_TOLERANCE_SECONDS).ifPresent(e -> {
            try {
                cachedCopierToleranceSeconds = Integer.parseInt(e.getConfValue());
            } catch (NumberFormatException ex) {}
        });
        repository.findById(KEY_COPIER_INTERVAL_MINS).ifPresent(e -> {
            try {
                cachedCopierIntervalMins = Integer.parseInt(e.getConfValue());
            } catch (NumberFormatException ex) {}
        });
        repository.findById(KEY_COPIER_USE_STAGE_1).ifPresent(e -> {
            cachedCopierUseStage1 = Boolean.parseBoolean(e.getConfValue());
        });
        repository.findById(KEY_COPIER_USE_STAGE_2).ifPresent(e -> {
            cachedCopierUseStage2 = Boolean.parseBoolean(e.getConfValue());
        });
        repository.findById(KEY_COPIER_STAGE_2_TOLERANCE).ifPresent(e -> {
            try {
                cachedCopierStage2Tolerance = Double.parseDouble(e.getConfValue());
            } catch (NumberFormatException ex) {}
        });
        repository.findById(KEY_COPIER_USE_STAGE_3).ifPresent(e -> {
            cachedCopierUseStage3 = Boolean.parseBoolean(e.getConfValue());
        });
        
        // Load Network Monitoring Config
        repository.findById(KEY_MAINTENANCE_TIMEOUT_MINS).ifPresent(e -> {
            try { cachedMaintenanceTimeoutMins = Integer.parseInt(e.getConfValue()); } catch(Exception ex){}
        });
        repository.findById(KEY_NETWORK_OFFLINE_THRESHOLD_MINS).ifPresent(e -> {
            try { cachedNetworkOfflineThresholdMins = Integer.parseInt(e.getConfValue()); } catch(Exception ex){}
        });
    }

    // --- Copier Verification Config ---
    public static final String KEY_COPIER_TOLERANCE_SECONDS = "COPIER_TOLERANCE_SECONDS";
    public static final String KEY_COPIER_INTERVAL_MINS = "COPIER_INTERVAL_MINS";
    public static final String KEY_COPIER_USE_STAGE_1 = "COPIER_USE_STAGE_1";
    public static final String KEY_COPIER_USE_STAGE_2 = "COPIER_USE_STAGE_2";
    public static final String KEY_COPIER_STAGE_2_TOLERANCE = "COPIER_STAGE_2_TOLERANCE";
    public static final String KEY_COPIER_USE_STAGE_3 = "COPIER_USE_STAGE_3";

    private int cachedCopierToleranceSeconds = 60; // default 60s
    private int cachedCopierIntervalMins = 10; // default 10 mins
    private boolean cachedCopierUseStage1 = true;
    private boolean cachedCopierUseStage2 = true;
    private double cachedCopierStage2Tolerance = 0.00001;
    private boolean cachedCopierUseStage3 = true;

    public int getCopierToleranceSeconds() {
        return cachedCopierToleranceSeconds;
    }

    public int getCopierIntervalMins() {
        return cachedCopierIntervalMins;
    }

    public boolean isCopierUseStage1() {
        return cachedCopierUseStage1;
    }

    public boolean isCopierUseStage2() {
        return cachedCopierUseStage2;
    }

    public boolean isCopierUseStage3() {
        return cachedCopierUseStage3;
    }

    public double getCopierStage2Tolerance() {
        return cachedCopierStage2Tolerance;
    }

    public void saveCopierConfig(int toleranceSeconds, int intervalMins) {
        this.cachedCopierToleranceSeconds = toleranceSeconds;
        this.cachedCopierIntervalMins = intervalMins;
        repository.save(new GlobalConfigEntity(KEY_COPIER_TOLERANCE_SECONDS, String.valueOf(toleranceSeconds)));
        repository.save(new GlobalConfigEntity(KEY_COPIER_INTERVAL_MINS, String.valueOf(intervalMins)));
    }

    public void saveCopierStageConfig(boolean useStage1, boolean useStage2, double stage2Tolerance, boolean useStage3) {
        this.cachedCopierUseStage1 = useStage1;
        this.cachedCopierUseStage2 = useStage2;
        this.cachedCopierStage2Tolerance = stage2Tolerance;
        this.cachedCopierUseStage3 = useStage3;
        repository.save(new GlobalConfigEntity(KEY_COPIER_USE_STAGE_1, String.valueOf(useStage1)));
        repository.save(new GlobalConfigEntity(KEY_COPIER_USE_STAGE_2, String.valueOf(useStage2)));
        repository.save(new GlobalConfigEntity(KEY_COPIER_STAGE_2_TOLERANCE, String.valueOf(stage2Tolerance)));
        repository.save(new GlobalConfigEntity(KEY_COPIER_USE_STAGE_3, String.valueOf(useStage3)));
    }

    // --- Network Monitoring Config ---
    public static final String KEY_MAINTENANCE_TIMEOUT_MINS = "MAINTENANCE_TIMEOUT_MINS";
    public static final String KEY_NETWORK_OFFLINE_THRESHOLD_MINS = "NETWORK_OFFLINE_THRESHOLD_MINS";

    private int cachedMaintenanceTimeoutMins = 20; // default 20 minutes
    private int cachedNetworkOfflineThresholdMins = 5; // default 5 minutes

    public int getMaintenanceTimeoutMins() {
        return cachedMaintenanceTimeoutMins;
    }

    public void setMaintenanceTimeoutMins(int mins) {
        this.cachedMaintenanceTimeoutMins = mins;
        repository.save(new GlobalConfigEntity(KEY_MAINTENANCE_TIMEOUT_MINS, String.valueOf(mins)));
    }

    public int getNetworkOfflineThresholdMins() {
        return cachedNetworkOfflineThresholdMins;
    }

    public void setNetworkOfflineThresholdMins(int mins) {
        this.cachedNetworkOfflineThresholdMins = mins;
        repository.save(new GlobalConfigEntity(KEY_NETWORK_OFFLINE_THRESHOLD_MINS, String.valueOf(mins)));
    }

    // getMagicNumberMaxAge() and setMagicNumberMaxAge() removed — now per-account

    public int getTradeSyncIntervalSeconds() {
        return cachedSyncIntervalSeconds;
    }

    public void setTradeSyncIntervalSeconds(int seconds) {
        this.cachedSyncIntervalSeconds = seconds;
        GlobalConfigEntity entity = new GlobalConfigEntity(KEY_TRADE_SYNC_INTERVAL, String.valueOf(seconds));
        repository.save(entity);
    }

    // --- Live Indicator Configuration Methods ---

    public int getLiveGreenMins() {
        return cachedLiveGreenMins;
    }

    public int getLiveYellowMins() {
        return cachedLiveYellowMins;
    }

    public int getLiveOrangeMins() {
        return cachedLiveOrangeMins;
    }

    public String getLiveColorGreen() {
        return cachedLiveColorGreen;
    }

    public String getLiveColorYellow() {
        return cachedLiveColorYellow;
    }

    public String getLiveColorOrange() {
        return cachedLiveColorOrange;
    }

    public String getLiveColorRed() {
        return cachedLiveColorRed;
    }

    public void saveLiveIndicatorConfig(int greenMins, int yellowMins, int orangeMins,
            String colorGreen, String colorYellow, String colorOrange, String colorRed) {
        this.cachedLiveGreenMins = greenMins;
        this.cachedLiveYellowMins = yellowMins;
        this.cachedLiveOrangeMins = orangeMins;
        this.cachedLiveColorGreen = colorGreen;
        this.cachedLiveColorYellow = colorYellow;
        this.cachedLiveColorOrange = colorOrange;
        this.cachedLiveColorRed = colorRed;

        repository.save(new GlobalConfigEntity(KEY_LIVE_GREEN_MINS, String.valueOf(greenMins)));
        repository.save(new GlobalConfigEntity(KEY_LIVE_YELLOW_MINS, String.valueOf(yellowMins)));
        repository.save(new GlobalConfigEntity(KEY_LIVE_ORANGE_MINS, String.valueOf(orangeMins)));
        repository.save(new GlobalConfigEntity(KEY_LIVE_COLOR_GREEN, colorGreen));
        repository.save(new GlobalConfigEntity(KEY_LIVE_COLOR_YELLOW, colorYellow));
        repository.save(new GlobalConfigEntity(KEY_LIVE_COLOR_ORANGE, colorOrange));
        repository.save(new GlobalConfigEntity(KEY_LIVE_COLOR_RED, colorRed));
    }

    // --- Mail Configuration Methods ---

    public String getMailHost() {
        return repository.findById(KEY_MAIL_HOST).map(GlobalConfigEntity::getConfValue).orElse("mail.gmx.net");
    }

    public int getMailPort() {
        return repository.findById(KEY_MAIL_PORT).map(e -> Integer.parseInt(e.getConfValue())).orElse(587);
    }

    public String getMailUser() {
        return repository.findById(KEY_MAIL_USER).map(GlobalConfigEntity::getConfValue).orElse("");
    }

    public String getMailPassword() {
        return repository.findById(KEY_MAIL_PASSWORD).map(GlobalConfigEntity::getConfValue).orElse("");
    }

    public String getMailFrom() {
        return repository.findById(KEY_MAIL_FROM).map(GlobalConfigEntity::getConfValue)
                .orElse("trade-monitor@localhost");
    }

    public String getMailTo() {
        return repository.findById(KEY_MAIL_TO).map(GlobalConfigEntity::getConfValue).orElse("");
    }

    public int getMailMaxPerDay() {
        return repository.findById(KEY_MAIL_MAX_PER_DAY).map(e -> Integer.parseInt(e.getConfValue())).orElse(10);
    }

    public void saveMailConfig(String host, int port, String user, String password, String from, String to,
            int maxPerDay) {
        repository.save(new GlobalConfigEntity(KEY_MAIL_HOST, host));
        repository.save(new GlobalConfigEntity(KEY_MAIL_PORT, String.valueOf(port)));
        repository.save(new GlobalConfigEntity(KEY_MAIL_USER, user));
        if (password != null && !password.isEmpty()) {
            repository.save(new GlobalConfigEntity(KEY_MAIL_PASSWORD, password));
        }
        repository.save(new GlobalConfigEntity(KEY_MAIL_FROM, from));
        repository.save(new GlobalConfigEntity(KEY_MAIL_TO, to));
        repository.save(new GlobalConfigEntity(KEY_MAIL_MAX_PER_DAY, String.valueOf(maxPerDay)));
    }
    // --- Homey Configuration Methods ---

    public static final String KEY_HOMEY_ID = "HOMEY_ID";
    public static final String KEY_HOMEY_EVENT = "HOMEY_EVENT";
    public static final String KEY_HOMEY_TRIGGER_SYNC = "HOMEY_TRIGGER_SYNC";
    public static final String KEY_HOMEY_TRIGGER_API = "HOMEY_TRIGGER_API";
    public static final String KEY_HOMEY_TRIGGER_HEALTH = "HOMEY_TRIGGER_HEALTH";
    public static final String KEY_HOMEY_TRIGGER_SECURITY = "HOMEY_TRIGGER_SECURITY";
    public static final String KEY_HOMEY_TRIGGER_OFFLINE = "HOMEY_TRIGGER_OFFLINE";
    public static final String KEY_HOMEY_REPEAT_COUNT = "HOMEY_REPEAT_COUNT";
    public static final String KEY_SYNC_ALARM_DELAY_MINS = "SYNC_ALARM_DELAY_MINS";

    private int cachedSyncAlarmDelayMins = 5; // Default 5 minutes

    public String getHomeyId() {
        return repository.findById(KEY_HOMEY_ID).map(GlobalConfigEntity::getConfValue)
                .orElse("");
    }

    public int getSyncAlarmDelayMins() {
        return cachedSyncAlarmDelayMins;
    }

    public String getHomeyEvent() {
        return repository.findById(KEY_HOMEY_EVENT).map(GlobalConfigEntity::getConfValue).orElse("sirene_an");
    }

    public int getHomeyRepeatCount() {
        return repository.findById(KEY_HOMEY_REPEAT_COUNT).map(e -> Integer.parseInt(e.getConfValue())).orElse(1);
    }

    public boolean isHomeyTriggerSync() {
        return repository.findById(KEY_HOMEY_TRIGGER_SYNC).map(e -> Boolean.parseBoolean(e.getConfValue()))
                .orElse(false);
    }

    public boolean isHomeyTriggerApi() {
        return repository.findById(KEY_HOMEY_TRIGGER_API).map(e -> Boolean.parseBoolean(e.getConfValue()))
                .orElse(false);
    }

    public boolean isHomeyTriggerHealth() {
        return repository.findById(KEY_HOMEY_TRIGGER_HEALTH).map(e -> Boolean.parseBoolean(e.getConfValue()))
                .orElse(false);
    }

    public boolean isHomeyTriggerSecurity() {
        return repository.findById(KEY_HOMEY_TRIGGER_SECURITY).map(e -> Boolean.parseBoolean(e.getConfValue()))
                .orElse(false);
    }

    public boolean isHomeyTriggerOffline() {
        return repository.findById(KEY_HOMEY_TRIGGER_OFFLINE).map(e -> Boolean.parseBoolean(e.getConfValue()))
                .orElse(false);
    }

    public void saveHomeyConfig(String homeyId, String eventName, boolean triggerSync, boolean triggerApi,
            boolean triggerHealth, boolean triggerSecurity, boolean triggerOffline,
            int repeatCount, int syncAlarmDelayMins) {
        repository.save(new GlobalConfigEntity(KEY_HOMEY_ID, homeyId));
        repository.save(new GlobalConfigEntity(KEY_HOMEY_EVENT, eventName));
        repository.save(new GlobalConfigEntity(KEY_HOMEY_TRIGGER_SYNC, String.valueOf(triggerSync)));
        repository.save(new GlobalConfigEntity(KEY_HOMEY_TRIGGER_API, String.valueOf(triggerApi)));
        repository.save(new GlobalConfigEntity(KEY_HOMEY_TRIGGER_HEALTH, String.valueOf(triggerHealth)));
        repository.save(new GlobalConfigEntity(KEY_HOMEY_TRIGGER_SECURITY, String.valueOf(triggerSecurity)));
        repository.save(new GlobalConfigEntity(KEY_HOMEY_TRIGGER_OFFLINE, String.valueOf(triggerOffline)));
        repository.save(new GlobalConfigEntity(KEY_HOMEY_REPEAT_COUNT, String.valueOf(repeatCount)));
        repository.save(new GlobalConfigEntity(KEY_SYNC_ALARM_DELAY_MINS, String.valueOf(syncAlarmDelayMins)));
        this.cachedSyncAlarmDelayMins = syncAlarmDelayMins;
    }

    // --- Sync Exemptions ---

    public static final String KEY_SYNC_EXEMPT_MAGIC_NUMBERS = "SYNC_EXEMPT_MAGIC_NUMBERS";

    /**
     * Returns the set of magic numbers excluded from the sync check.
     * Trades with these magic numbers will get status EXEMPTED (not WARNING) and
     * won't trigger alarms.
     */
    public Set<Long> getSyncExemptMagicNumbers() {
        String raw = repository.findById(KEY_SYNC_EXEMPT_MAGIC_NUMBERS)
                .map(GlobalConfigEntity::getConfValue).orElse("");
        if (raw == null || raw.isBlank())
            return Collections.emptySet();
        Set<Long> result = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    result.add(Long.parseLong(trimmed));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    // --- Log Retention Variables ---
    public int getLogLoginDays() {
        return cachedLogLoginDays;
    }

    public int getLogConnDays() {
        return cachedLogConnDays;
    }

    public int getLogClientDays() {
        return cachedLogClientDays;
    }

    public int getLogEaDays() {
        return cachedLogEaDays;
    }

    public void saveLogRetentionConfig(int loginDays, int connDays, int clientDays) {
        saveLogRetentionConfig(loginDays, connDays, clientDays, cachedLogEaDays);
    }

    public void saveLogRetentionConfig(int loginDays, int connDays, int clientDays, int eaDays) {
        this.cachedLogLoginDays = loginDays;
        this.cachedLogConnDays = connDays;
        this.cachedLogClientDays = clientDays;
        this.cachedLogEaDays = eaDays;
        repository.save(new GlobalConfigEntity(KEY_LOG_LOGIN_DAYS, String.valueOf(loginDays)));
        repository.save(new GlobalConfigEntity(KEY_LOG_CONN_DAYS, String.valueOf(connDays)));
        repository.save(new GlobalConfigEntity(KEY_LOG_CLIENT_DAYS, String.valueOf(clientDays)));
        repository.save(new GlobalConfigEntity(KEY_LOG_EA_DAYS, String.valueOf(eaDays)));
    }

    public void setSyncExemptMagicNumbers(Set<Long> magicNumbers) {
        String value = magicNumbers.stream().map(String::valueOf).collect(Collectors.joining(","));
        repository.save(new GlobalConfigEntity(KEY_SYNC_EXEMPT_MAGIC_NUMBERS, value));
    }

    // --- Security Config Getters ---
    public boolean isSecRateLimitEnabled() {
        return cachedSecRateLimitEnabled;
    }

    public int getSecRateLimitPerMin() {
        return cachedSecRateLimitPerMin;
    }

    public boolean isSecBruteForceEnabled() {
        return cachedSecBruteForceEnabled;
    }

    public int getSecBruteForceMaxAttempts() {
        return cachedSecBruteForceMaxAttempts;
    }

    public int getSecBruteForceLockoutMins() {
        return cachedSecBruteForceLockoutMins;
    }

    public boolean isSecHeadersEnabled() {
        return cachedSecHeadersEnabled;
    }

    public int getSecMaxSessions() {
        return cachedSecMaxSessions;
    }

    public boolean isSecH2ConsoleEnabled() {
        return cachedSecH2ConsoleEnabled;
    }

    public void saveSecurityConfig(boolean rateLimitEnabled, int rateLimitPerMin,
            boolean bruteForceEnabled, int bruteForceMaxAttempts, int bruteForceLockoutMins,
            boolean headersEnabled, int maxSessions, boolean h2ConsoleEnabled) {
        this.cachedSecRateLimitEnabled = rateLimitEnabled;
        this.cachedSecRateLimitPerMin = rateLimitPerMin;
        this.cachedSecBruteForceEnabled = bruteForceEnabled;
        this.cachedSecBruteForceMaxAttempts = bruteForceMaxAttempts;
        this.cachedSecBruteForceLockoutMins = bruteForceLockoutMins;
        this.cachedSecHeadersEnabled = headersEnabled;
        this.cachedSecMaxSessions = maxSessions;
        this.cachedSecH2ConsoleEnabled = h2ConsoleEnabled;

        repository.save(new GlobalConfigEntity(KEY_SEC_RATE_LIMIT_ENABLED, String.valueOf(rateLimitEnabled)));
        repository.save(new GlobalConfigEntity(KEY_SEC_RATE_LIMIT_PER_MIN, String.valueOf(rateLimitPerMin)));
        repository.save(new GlobalConfigEntity(KEY_SEC_BRUTE_FORCE_ENABLED, String.valueOf(bruteForceEnabled)));
        repository
                .save(new GlobalConfigEntity(KEY_SEC_BRUTE_FORCE_MAX_ATTEMPTS, String.valueOf(bruteForceMaxAttempts)));
        repository
                .save(new GlobalConfigEntity(KEY_SEC_BRUTE_FORCE_LOCKOUT_MINS, String.valueOf(bruteForceLockoutMins)));
        repository.save(new GlobalConfigEntity(KEY_SEC_HEADERS_ENABLED, String.valueOf(headersEnabled)));
        repository.save(new GlobalConfigEntity(KEY_SEC_MAX_SESSIONS, String.valueOf(maxSessions)));
        repository.save(new GlobalConfigEntity(KEY_SEC_H2_CONSOLE_ENABLED, String.valueOf(h2ConsoleEnabled)));
    }

    // --- Broker Commission Factor Methods ---

    /**
     * Returns the commission factor for the given broker name.
     * If no factor is configured, returns 1.0 (no change).
     */
    public double getBrokerCommissionFactor(String brokerName) {
        if (brokerName == null || brokerName.isEmpty()) return 1.0;
        return cachedBrokerCommFactors.getOrDefault(brokerName, 1.0);
    }

    /**
     * Returns all configured broker commission factors (for admin UI).
     */
    public Map<String, Double> getAllBrokerCommFactors() {
        return new LinkedHashMap<>(cachedBrokerCommFactors);
    }

    /**
     * Saves a broker commission factor to DB and cache.
     */
    public void saveBrokerCommFactor(String brokerName, double factor) {
        cachedBrokerCommFactors.put(brokerName, factor);
        repository.save(new GlobalConfigEntity(KEY_BROKER_COMM_FACTOR_PREFIX + brokerName, String.valueOf(factor)));
    }

    /**
     * Deletes a broker commission factor from DB and cache.
     */
    public void deleteBrokerCommFactor(String brokerName) {
        cachedBrokerCommFactors.remove(brokerName);
        repository.deleteById(KEY_BROKER_COMM_FACTOR_PREFIX + brokerName);
    }
}
