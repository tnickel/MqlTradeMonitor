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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Autowired
        private CustomUserDetailsService customUserDetailsService;

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
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                // Disable CSRF for API endpoints used by EA
                                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/api/register", "/api/trades-init", "/api/trades",
                                                                "/api/heartbeat", "/api/history", "/css/**", "/js/**",
                                                                "/images/**", "/login",
                                                                "/mobile/**")
                                                .permitAll()
                                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                                .anyRequest().authenticated())
                                .formLogin(form -> form
                                                .loginPage("/login")
                                                .defaultSuccessUrl("/", true)
                                                .permitAll())
                                .logout(logout -> logout
                                                .logoutRequestMatcher(
                                                                new org.springframework.security.web.util.matcher.AntPathRequestMatcher(
                                                                                "/logout"))
                                                .logoutSuccessUrl("/login?logout")
                                                .permitAll())
                                .authenticationProvider(authenticationProvider());

                return http.build();
        }
}
