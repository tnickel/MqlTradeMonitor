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

    // Cache the value to avoid hitting DB on every request
    private int cachedMaxAgeDays = 30; // Default 30 days

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
    }

    public int getMagicNumberMaxAge() {
        return cachedMaxAgeDays;
    }

    public void setMagicNumberMaxAge(int days) {
        this.cachedMaxAgeDays = days;
        GlobalConfigEntity entity = new GlobalConfigEntity(KEY_MAGIC_MAX_AGE, String.valueOf(days));
        repository.save(entity);
    }
}
