package de.trademonitor.controller;

import de.trademonitor.entity.UserEntity;
import de.trademonitor.security.CustomUserDetails;
import de.trademonitor.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ApiAuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private DaoAuthenticationProvider authenticationProvider;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials, HttpServletRequest request) {
        try {
            String username = credentials.get("username");
            String password = credentials.get("password");
            if (username == null || password == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username and password required"));
            }
            
            UsernamePasswordAuthenticationToken authReq =
                new UsernamePasswordAuthenticationToken(username, password);
            Authentication auth = authenticationProvider.authenticate(authReq);
            
            SecurityContext sc = SecurityContextHolder.getContext();
            sc.setAuthentication(auth);
            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sc);
            
            return ResponseEntity.ok(Map.of("message", "Login successful"));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }

    @PostMapping("/demo-login")
    public ResponseEntity<?> demoLogin(HttpServletRequest request) {
        try {
            String demoUsername = "demouser";
            
            UserEntity demoUser = userService.getUserByUsername(demoUsername).orElseGet(() -> {
                return userService.createUser(demoUsername, UUID.randomUUID().toString(), "ROLE_DEMO", new HashSet<>());
            });

            CustomUserDetails userDetails = new CustomUserDetails(demoUser);
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            
            SecurityContext securityContext = SecurityContextHolder.getContext();
            securityContext.setAuthentication(authentication);

            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

            return ResponseEntity.ok(Map.of("message", "Demo login successful"));
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
