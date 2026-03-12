package de.trademonitor.controller;

import de.trademonitor.entity.UserEntity;
import de.trademonitor.security.CustomUserDetails;
import de.trademonitor.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.HashSet;
import java.util.UUID;

@Controller
public class DemoLoginController {

    @Autowired
    private UserService userService;

    @PostMapping("/demo-login")
    public String demoLogin(HttpServletRequest request) {
        String demoUsername = "demouser";
        
        UserEntity demoUser = userService.getUserByUsername(demoUsername).orElseGet(() -> {
            // Create demo user with a random UUID as password to prevent manual login
            return userService.createUser(demoUsername, UUID.randomUUID().toString(), "ROLE_DEMO", new HashSet<>());
        });

        // Programmatic authentication
        CustomUserDetails userDetails = new CustomUserDetails(demoUser);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(authentication);

        // Required to keep authentication in session
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        return "redirect:/";
    }
}
