package de.trademonitor.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Properties;

@Service
public class EmailService {

    @Autowired
    private GlobalConfigService configService;

    // Rate Limiting Logic
    private LocalDate lastResetDate = LocalDate.now();
    private int emailsSentToday = 0;

    /**
     * Sends a warning email if the daily limit hasn't been reached and config is
     * valid.
     */
    public synchronized void sendSyncWarningEmail(String subject, String body) {
        // 1. Check Rate Limit
        LocalDate today = LocalDate.now();
        if (!today.isEqual(lastResetDate)) {
            emailsSentToday = 0;
            lastResetDate = today;
        }

        int maxPerDay = configService.getMailMaxPerDay();
        if (emailsSentToday >= maxPerDay) {
            System.out.println(
                    "Email limit reached for today (" + emailsSentToday + "/" + maxPerDay + "). Skipping email.");
            return;
        }

        // 2. Validate Config
        String to = configService.getMailTo();
        if (to == null || to.isEmpty()) {
            System.out.println("Email 'To' address not configured. Skipping email.");
            return;
        }

        String host = configService.getMailHost();
        if (host == null || host.isEmpty()) {
            System.out.println("Email 'Host' not configured. Skipping email.");
            return;
        }

        // 3. Configure Sender
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(host);
            sender.setPort(configService.getMailPort());
            sender.setUsername(configService.getMailUser());
            sender.setPassword(configService.getMailPassword());

            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            // Increase timeout
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "5000");
            props.put("mail.smtp.writetimeout", "5000");

            // 4. Create and Send Message
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(configService.getMailFrom());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            sender.send(message);

            emailsSentToday++;
            System.out.println("Email sent to " + to + ". Daily count: " + emailsSentToday + "/" + maxPerDay);

        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
