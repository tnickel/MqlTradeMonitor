package de.trademonitor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduled production jobs without starting them in the isolated test
 * profile.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
