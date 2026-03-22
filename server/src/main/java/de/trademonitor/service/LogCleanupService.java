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
    private de.trademonitor.repository.ClientActionCounterRepository clientActionCounterRepository;

    @Autowired
    private de.trademonitor.repository.ClientErrorLogRepository clientErrorLogRepository;

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
                java.time.LocalDate dateCutoff = java.time.LocalDate.now().minusDays(clientDays);
                clientActionCounterRepository.deleteByDateBefore(dateCutoff);
                System.out.println("Deleted Client Action Counters older than " + clientDays + " days.");

                LocalDateTime timeCutoff = LocalDateTime.now().minusDays(clientDays);
                clientErrorLogRepository.deleteByTimestampBefore(timeCutoff);
                System.out.println("Deleted Client Error Logs older than " + clientDays + " days.");
            }

        } catch (Exception e) {
            System.err.println("Error during log cleanup routine: " + e.getMessage());
        }

        System.out.println("Finished daily log cleanup routine.");
    }
}
