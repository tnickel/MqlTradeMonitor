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

    @Autowired
    private de.trademonitor.security.BruteForceProtectionService bruteForceProtectionService;

    @Autowired
    private de.trademonitor.service.SecurityAuditService securityAuditService;

    @Override
    public void onApplicationEvent(AbstractAuthenticationEvent event) {
        String ipAddress = "Unknown";
        
        // Try getting IP from HTTP request headers directly (X-Forwarded-For handles Nginx proxy)
        org.springframework.web.context.request.RequestAttributes reqAttr = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (reqAttr instanceof org.springframework.web.context.request.ServletRequestAttributes) {
            jakarta.servlet.http.HttpServletRequest request = ((org.springframework.web.context.request.ServletRequestAttributes) reqAttr).getRequest();
            ipAddress = request.getHeader("X-Forwarded-For");
            if (ipAddress == null || ipAddress.trim().isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getRemoteAddr();
            } else {
                // X-Forwarded-For can be a comma-separated list, the first one is the client.
                ipAddress = ipAddress.split(",")[0].trim();
            }
        } else {
            // Fallback to Spring Security Details if not in a request context
            Object details = event.getAuthentication().getDetails();
            if (details instanceof WebAuthenticationDetails) {
                ipAddress = ((WebAuthenticationDetails) details).getRemoteAddress();
            }
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
            bruteForceProtectionService.recordSuccess(ipAddress);

            // Trigger dynamic Fail2Ban IP Whitelist for admins
            boolean isAdmin = successEvent.getAuthentication().getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (isAdmin && ipAddress != null && !ipAddress.equals("Unknown")) {
                // Run async to not block the login request significantly
                final String finalIp = ipAddress;
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    securityAuditService.syncFail2banWhitelist(finalIp);
                });
            }
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
            bruteForceProtectionService.recordFailure(ipAddress);
        }
    }
}
