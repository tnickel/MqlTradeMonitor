package de.trademonitor.config;

import de.trademonitor.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that blocks non-GET requests for users with ROLE_DEMO.
 */
@Component
public class ReadOnlyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@org.springframework.lang.NonNull HttpServletRequest request, 
                                    @org.springframework.lang.NonNull HttpServletResponse response, 
                                    @org.springframework.lang.NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod();
        
        // Allow all GET, HEAD, OPTIONS requests
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check if user is authenticated and has ROLE_DEMO
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
            if ("ROLE_DEMO".equals(userDetails.getUserEntity().getRole())) {
                // Allow logout
                if (request.getRequestURI().endsWith("/logout")) {
                    filterChain.doFilter(request, response);
                    return;
                }
                
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Demo user cannot modify data.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
