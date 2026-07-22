package de.trademonitor.config;

import de.trademonitor.security.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ReadOnlyFilter readOnlyFilter;

    @Autowired
    private H2ConsoleAccessFilter h2ConsoleAccessFilter;

    @Autowired
    private de.trademonitor.service.GlobalConfigService globalConfigService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(SessionRegistry sessionRegistry) {
        ConcurrentSessionControlAuthenticationStrategy concurrent =
                new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry) {
                    @Override
                    protected int getMaximumSessionsForThisUser(org.springframework.security.core.Authentication authentication) {
                        return Math.max(1, Math.min(50, globalConfigService.getSecMaxSessions()));
                    }
                };
        concurrent.setExceptionIfMaximumExceeded(false);
        return new CompositeSessionAuthenticationStrategy(List.of(
                concurrent,
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(sessionRegistry)));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            SessionRegistry sessionRegistry,
            SessionAuthenticationStrategy sessionAuthenticationStrategy) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Disable CSRF for API endpoints used by EA and Admin AJAX
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        AntPathRequestMatcher.antMatcher("/api/**"),
                        AntPathRequestMatcher.antMatcher("/admin/api/**"),
                        AntPathRequestMatcher.antMatcher("/h2-console/**")
                ))
                .addFilterBefore(readOnlyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(h2ConsoleAccessFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new ConcurrentSessionFilter(sessionRegistry), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/h2-console/**")).hasRole("ADMIN")
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher("/api/register"),
                                AntPathRequestMatcher.antMatcher("/api/trades-init"),
                                AntPathRequestMatcher.antMatcher("/api/trades"),
                                AntPathRequestMatcher.antMatcher("/api/heartbeat"),
                                AntPathRequestMatcher.antMatcher("/api/history"),
                                AntPathRequestMatcher.antMatcher("/api/ea-logs"),
                                AntPathRequestMatcher.antMatcher("/api/upload-requested-ticks"),
                                AntPathRequestMatcher.antMatcher("/api/update/download"),
                                AntPathRequestMatcher.antMatcher("/css/**"),
                                AntPathRequestMatcher.antMatcher("/js/**"),
                                AntPathRequestMatcher.antMatcher("/img/**"),
                                AntPathRequestMatcher.antMatcher("/login"),
                                AntPathRequestMatcher.antMatcher("/demo-login"),
                                AntPathRequestMatcher.antMatcher("/impressum"),
                                AntPathRequestMatcher.antMatcher("/privacy"),
                                AntPathRequestMatcher.antMatcher("/mobile/**"),
                                AntPathRequestMatcher.antMatcher("/api/login"),
                                AntPathRequestMatcher.antMatcher("/api/demo-login"),
                                AntPathRequestMatcher.antMatcher("/api/logout"),
                                AntPathRequestMatcher.antMatcher("/api/latest-version"),
                                AntPathRequestMatcher.antMatcher("/trademonitor*.apk"),
                                AntPathRequestMatcher.antMatcher("/error")
                        ).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/admin/**")).hasRole("ADMIN")
                        .anyRequest().authenticated())
                .sessionManagement(session -> session
                        .sessionAuthenticationStrategy(sessionAuthenticationStrategy))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json");
                                response.getWriter().write("{\"status\":\"error\",\"message\":\"Unauthorized\"}");
                            } else {
                                response.sendRedirect("/login");
                            }
                        })
                )
                .authenticationProvider(authenticationProvider());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Only the production host and local development are trusted. Credentialed
        // cross-origin requests are allowed (allowCredentials=true), so this list
        // must stay tight — never use broad third-party wildcards here.
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*",
            "https://monitor.tnickel-ki.de",
            "http://monitor.tnickel-ki.de",
            "https://*.tnickel-ki.de"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
