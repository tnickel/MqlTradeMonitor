package de.trademonitor.config;

import de.trademonitor.security.BruteForceProtectionService;
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

/**
 * Filter that adds security headers to responses and blocks brute-force login
 * attempts.
 * Security headers (CSP, X-Content-Type-Options, etc.) can be toggled via admin
 * config.
 * Brute-force check is applied to the /login endpoint.
 */
@Component
@Order(2)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Autowired
    private GlobalConfigService configService;

    @Autowired
    private BruteForceProtectionService bruteForceService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Brute-force check for login endpoint
        if (request.getRequestURI().equals("/login") && "POST".equalsIgnoreCase(request.getMethod())) {
            String ip = getClientIP(request);
            if (bruteForceService.isBlocked(ip)) {
                long remaining = bruteForceService.getRemainingLockoutSeconds(ip);
                response.setStatus(429);
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write(
                        "<html><body style='background:#0d1117;color:#c9d1d9;font-family:sans-serif;text-align:center;padding:50px;'>"
                                +
                                "<h1 style='color:#ef4444;'>&#x1F6AB; Zugang gesperrt</h1>" +
                                "<p>Zu viele fehlgeschlagene Login-Versuche von Ihrer IP-Adresse.</p>" +
                                "<p>Bitte versuchen Sie es in <strong>" + (remaining / 60 + 1)
                                + " Minuten</strong> erneut.</p>" +
                                "</body></html>");
                return;
            }
        }

        // Add security headers if enabled
        if (configService.isSecHeadersEnabled()) {
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "SAMEORIGIN");
            response.setHeader("X-XSS-Protection", "1; mode=block");
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            response.setHeader("Content-Security-Policy",
                    "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net https://cdn.tailwindcss.com https://cdnjs.cloudflare.com; " +
                            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com; " +
                            "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; " +
                            "img-src 'self' data: https://unpkg.com; " +
                            "connect-src 'self';");

            // Only enable HSTS if the request came over HTTPS
            if (request.isSecure()) {
                response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            }
        }

        // Remove server identification header
        response.setHeader("Server", "");

        filterChain.doFilter(request, response);
    }

    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
