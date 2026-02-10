package de.trademonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Trade Monitor Server.
 * Receives trade data from MetaTrader EAs and provides a web dashboard.
 */
@SpringBootApplication
@EnableScheduling
public class TradeMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeMonitorApplication.class, args);
    }
}
