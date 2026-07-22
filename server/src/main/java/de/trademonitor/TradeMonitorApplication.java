package de.trademonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Main application class for Trade Monitor Server.
 * Receives trade data from MetaTrader EAs and provides a web dashboard.
 */
@SpringBootApplication
public class TradeMonitorApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(TradeMonitorApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(TradeMonitorApplication.class, args);
    }
}
