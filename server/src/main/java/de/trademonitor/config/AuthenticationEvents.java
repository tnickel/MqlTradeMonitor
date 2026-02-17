package de.trademonitor.config;

import de.trademonitor.entity.LoginLog;
import de.trademonitor.repository.LoginLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEvents implements ApplicationListener<AbstractAuthenticationEvent> {

    @Autowired
    private LoginLogRepository loginLogRepository;

    @Override
    public void onApplicationEvent(AbstractAuthenticationEvent event) {
        String ipAddress = "Unknown";
        Object details = event.getAuthentication().getDetails();
        if (details instanceof WebAuthenticationDetails) {
            ipAddress = ((WebAuthenticationDetails) details).getRemoteAddress();
        }

        if (event instanceof AuthenticationSuccessEvent) {
            AuthenticationSuccessEvent successEvent = (AuthenticationSuccessEvent) event;
            String username = successEvent.getAuthentication().getName();

            LoginLog log = new LoginLog();
            log.setTimestamp(java.time.LocalDateTime.now());
            log.setUsername(username);
            log.setIpAddress(ipAddress);
            log.setSuccess(true);
            log.setDetails("Login Successful");
            loginLogRepository.save(log);
        } else if (event instanceof AuthenticationFailureBadCredentialsEvent) {
            AuthenticationFailureBadCredentialsEvent failEvent = (AuthenticationFailureBadCredentialsEvent) event;
            String username = (String) failEvent.getAuthentication().getPrincipal();

            LoginLog log = new LoginLog();
            log.setTimestamp(java.time.LocalDateTime.now());
            log.setUsername(username);
            log.setIpAddress(ipAddress);
            log.setSuccess(false);
            log.setDetails("Bad Credentials");
            loginLogRepository.save(log);
        }
    }
}
