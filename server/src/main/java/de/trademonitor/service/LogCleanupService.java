package de.trademonitor.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LogCleanupService {

    @Autowired
    private GlobalConfigService configService;

    @Autowired
    private de.trademonitor.repository.LoginLogRepository loginLogRepository;

    @Autowired
    private de.trademonitor.repository.RequestLogRepository requestLogRepository;

    @Autowired
    private de.trademonitor.repository.ClientLogRepository clientLogRepository;

    /**
     * Runs every day at 2:00 AM server time to clean up old logs.
     * Cron expression: Second Minute Hour Day Month Weekday
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldLogs() {
        System.out.println("Starting daily log cleanup routine...");

        try {
            int loginDays = configService.getLogLoginDays();
            if (loginDays > 0) {
                LocalDateTime cutoff = LocalDateTime.now().minusDays(loginDays);
                loginLogRepository.deleteByTimestampBefore(cutoff);
                System.out.println("Deleted Login Logs older than " + loginDays + " days.");
            }

            int connDays = configService.getLogConnDays();
            if (connDays > 0) {
                LocalDateTime cutoff = LocalDateTime.now().minusDays(connDays);
                requestLogRepository.deleteByTimestampBefore(cutoff);
                System.out.println("Deleted Connection (Request) Logs older than " + connDays + " days.");
            }

            int clientDays = configService.getLogClientDays();
            if (clientDays > 0) {
                LocalDateTime cutoff = LocalDateTime.now().minusDays(clientDays);
                clientLogRepository.deleteByTimestampBefore(cutoff);
                System.out.println("Deleted Client Logs older than " + clientDays + " days.");
            }

        } catch (Exception e) {
            System.err.println("Error during log cleanup routine: " + e.getMessage());
        }

        System.out.println("Finished daily log cleanup routine.");
    }
}
