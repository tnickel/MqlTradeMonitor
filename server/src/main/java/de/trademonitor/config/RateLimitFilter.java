package de.trademonitor.config;

import de.trademonitor.service.GlobalConfigService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiting filter.
 * Uses a sliding time window to track request counts per IP.
 * Returns HTTP 429 Too Many Requests when the configurable threshold is
 * exceeded.
 * Skips /api/** endpoints (MetaTrader EA traffic) to avoid disrupting automated
 * trading.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    @Autowired
    private GlobalConfigService configService;

    // IP -> [timestamp_minute, request_count]
    private final ConcurrentHashMap<String, long[]> requestCounts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Skip if rate limiting is disabled
        if (!configService.isSecRateLimitEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip API endpoints (MetaTrader EA communication)
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip static resources
        if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/images/")
                || uri.equals("/favicon.ico")) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = getClientIP(request);
        long currentMinute = System.currentTimeMillis() / 60000;
        int maxPerMin = configService.getSecRateLimitPerMin();

        long[] bucket = requestCounts.compute(ip, (key, existing) -> {
            if (existing == null || existing[0] != currentMinute) {
                return new long[] { currentMinute, 1 };
            }
            existing[1]++;
            return existing;
        });

        if (bucket[1] > maxPerMin) {
            response.setStatus(429);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("429 Too Many Requests - Rate limit exceeded. Try again later.");
            logger.warn("Rate limit exceeded for IP: " + ip + " (" + bucket[1] + " requests/min, limit: " + maxPerMin
                    + ")");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Cleanup stale entries. Called periodically.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300000) // every 5 minutes
    public void cleanupStaleEntries() {
        long currentMinute = System.currentTimeMillis() / 60000;
        requestCounts.entrySet().removeIf(entry -> entry.getValue()[0] < currentMinute - 2);
    }
}
