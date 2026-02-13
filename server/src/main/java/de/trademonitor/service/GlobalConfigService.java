package de.trademonitor.service;

import de.trademonitor.entity.GlobalConfigEntity;
import de.trademonitor.repository.GlobalConfigRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GlobalConfigService {

    public static final String KEY_MAGIC_MAX_AGE = "MAGIC_NUMBER_MAX_AGE_DAYS";

    @Autowired
    private GlobalConfigRepository repository;

    public static final String KEY_TRADE_SYNC_INTERVAL = "TRADE_SYNC_INTERVAL_SECONDS";

    // Mail Config Keys
    public static final String KEY_MAIL_HOST = "MAIL_HOST";
    public static final String KEY_MAIL_PORT = "MAIL_PORT";
    public static final String KEY_MAIL_USER = "MAIL_USER";
    public static final String KEY_MAIL_PASSWORD = "MAIL_PASSWORD";
    public static final String KEY_MAIL_FROM = "MAIL_FROM";
    public static final String KEY_MAIL_TO = "MAIL_TO";
    public static final String KEY_MAIL_MAX_PER_DAY = "MAIL_MAX_PER_DAY";

    // Cache the value to avoid hitting DB on every request
    private int cachedMaxAgeDays = 30; // Default 30 days
    private int cachedSyncIntervalSeconds = 60; // Default 60 seconds

    @PostConstruct
    public void init() {
        // Load on startup
        repository.findById(KEY_MAGIC_MAX_AGE).ifPresent(entity -> {
            try {
                cachedMaxAgeDays = Integer.parseInt(entity.getConfValue());
            } catch (NumberFormatException e) {
                System.err.println(
                        "Invalid number format for config " + KEY_MAGIC_MAX_AGE + ": " + entity.getConfValue());
            }
        });

        repository.findById(KEY_TRADE_SYNC_INTERVAL).ifPresent(entity -> {
            try {
                cachedSyncIntervalSeconds = Integer.parseInt(entity.getConfValue());
            } catch (NumberFormatException e) {
                System.err.println(
                        "Invalid number format for config " + KEY_TRADE_SYNC_INTERVAL + ": " + entity.getConfValue());
            }
        });
    }

    public int getMagicNumberMaxAge() {
        return cachedMaxAgeDays;
    }

    public void setMagicNumberMaxAge(int days) {
        this.cachedMaxAgeDays = days;
        GlobalConfigEntity entity = new GlobalConfigEntity(KEY_MAGIC_MAX_AGE, String.valueOf(days));
        repository.save(entity);
    }

    public int getTradeSyncIntervalSeconds() {
        return cachedSyncIntervalSeconds;
    }

    public void setTradeSyncIntervalSeconds(int seconds) {
        this.cachedSyncIntervalSeconds = seconds;
        GlobalConfigEntity entity = new GlobalConfigEntity(KEY_TRADE_SYNC_INTERVAL, String.valueOf(seconds));
        repository.save(entity);
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
}
