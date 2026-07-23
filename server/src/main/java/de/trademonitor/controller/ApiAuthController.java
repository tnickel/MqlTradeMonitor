package de.trademonitor.controller;

import de.trademonitor.entity.UserEntity;
import de.trademonitor.security.CustomUserDetails;
import de.trademonitor.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.AuthenticationException;

@RestController
@RequestMapping("/api")
public class ApiAuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private DaoAuthenticationProvider authenticationProvider;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials,
            HttpServletRequest request, HttpServletResponse response, CsrfToken csrfToken) {
        if (credentials == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body required"));
        }
        String username = credentials.get("username");
        String password = credentials.get("password");
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password required"));
        }

        UsernamePasswordAuthenticationToken authReq =
            new UsernamePasswordAuthenticationToken(username, password);
        try {
            Authentication auth = authenticationProvider.authenticate(authReq);

            // Apply the same session fixation protection, concurrency limit and
            // registry bookkeeping as the form login.
            request.getSession(true);
            sessionAuthenticationStrategy.onAuthentication(auth, request, response);

            SecurityContext sc = SecurityContextHolder.getContext();
            sc.setAuthentication(auth);
            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sc);

            eventPublisher.publishEvent(new AuthenticationSuccessEvent(auth));
            
            return ResponseEntity.ok(Map.of(
                    "message", "Login successful",
                    "csrfToken", csrfToken.getToken(),
                    "csrfHeader", csrfToken.getHeaderName()));
        } catch (AuthenticationException e) {
            eventPublisher.publishEvent(new AuthenticationFailureBadCredentialsEvent(authReq, e));
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        } catch (Exception e) {
            eventPublisher.publishEvent(new AuthenticationFailureBadCredentialsEvent(authReq, new org.springframework.security.authentication.BadCredentialsException(e.getMessage())));
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }

    @PostMapping("/demo-login")
    public ResponseEntity<?> demoLogin(HttpServletRequest request, HttpServletResponse response,
            CsrfToken csrfToken) {
        try {
            String demoUsername = "demouser";

            UserEntity demoUser = userService.getUserByUsername(demoUsername).orElse(null);
            if (demoUser == null) {
                try {
                    demoUser = userService.createUser(demoUsername, UUID.randomUUID().toString(), "ROLE_DEMO", new HashSet<>());
                } catch (IllegalArgumentException e) {
                    demoUser = userService.getUserByUsername(demoUsername)
                            .orElseThrow(() -> new IllegalStateException("Demo user could not be created"));
                }
            }

            CustomUserDetails userDetails = new CustomUserDetails(demoUser);
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            request.getSession(true);
            sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

            SecurityContext securityContext = SecurityContextHolder.getContext();
            securityContext.setAuthentication(authentication);

            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

            return ResponseEntity.ok(Map.of(
                    "message", "Demo login successful",
                    "csrfToken", csrfToken.getToken(),
                    "csrfHeader", csrfToken.getHeaderName()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Demo login failed: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }
}
