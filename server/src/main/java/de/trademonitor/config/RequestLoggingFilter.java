package de.trademonitor.config;

import de.trademonitor.entity.RequestLog;
import de.trademonitor.repository.RequestLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Autowired
    private RequestLogRepository requestLogRepository;

    private final java.util.Set<String> knownIps = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @jakarta.annotation.PostConstruct
    public void init() {
        // Load historically known IPs to prevent re-logging after restart
        try {
            java.util.List<String> existingIps = requestLogRepository.findDistinctIpAddresses();
            if (existingIps != null) {
                knownIps.addAll(existingIps);
            }
        } catch (Exception e) {
            logger.error("Failed to load existing IPs for logging filter", e);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Skip static resources and H2 console
        String uri = request.getRequestURI();
        if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/images/")
                || uri.equals("/favicon.ico") || uri.startsWith("/h2-console")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Log after request is processed to capture status code
            try {
                String ipAddress = request.getRemoteAddr();

                // Only log if this IP is NEW (not seen before)
                if (knownIps.add(ipAddress)) {
                    String method = request.getMethod();
                    String queryString = request.getQueryString();
                    String userAgent = request.getHeader("User-Agent");
                    if (userAgent != null && userAgent.length() > 1000) {
                        userAgent = userAgent.substring(0, 1000); // Truncate to fit column
                    }

                    int statusCode = response.getStatus();

                    RequestLog log = new RequestLog(ipAddress, method, uri, queryString, userAgent, statusCode);
                    requestLogRepository.save(log);
                }
            } catch (Exception e) {
                // Failsafe logging - do not break request flow if logging fails
                logger.error("Failed to save request log", e);
            }
        }
    }
}
