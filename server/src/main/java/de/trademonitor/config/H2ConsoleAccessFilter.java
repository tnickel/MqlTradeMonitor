package de.trademonitor.config;

import de.trademonitor.service.GlobalConfigService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Hides the H2 servlet completely while the admin switch is disabled. */
@Component
public class H2ConsoleAccessFilter extends OncePerRequestFilter {

    private final GlobalConfigService configService;
    private final String databasePassword;

    public H2ConsoleAccessFilter(GlobalConfigService configService,
            @Value("${spring.datasource.password:}") String databasePassword) {
        this.configService = configService;
        this.databasePassword = databasePassword;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/h2-console");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!configService.isSecH2ConsoleEnabled()
                || databasePassword == null || databasePassword.isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
