package de.trademonitor.controller;

import de.trademonitor.security.CustomUserDetails;
import de.trademonitor.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("")
    public String showProfile(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUserEntity());
        }
        return "profile";
    }

    @PostMapping("/change-password")
    public String changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            RedirectAttributes redirectAttrs) {

        if (userDetails == null) {
            return "redirect:/login";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirectAttrs.addFlashAttribute("errorMessage", "Passwörter stimmen nicht überein!");
            return "redirect:/profile";
        }

        if ("ROLE_DEMO".equals(userDetails.getUserEntity().getRole())) {
            redirectAttrs.addFlashAttribute("errorMessage", "Demo-User können ihr Passwort nicht ändern!");
            return "redirect:/profile";
        }

        try {
            userService.changePassword(userDetails.getUserEntity().getId(), newPassword);

            // Log out the user programmatically
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null) {
                new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler()
                        .logout(request, response, auth);
            }

            return "redirect:/login?logout";
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", "Fehler beim Ändern des Passworts: " + e.getMessage());
            return "redirect:/profile";
        }
    }

    @Autowired
    private de.trademonitor.service.AccountManager accountManager;

    @GetMapping("/app-config")
    public String showAppConfig(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        
        de.trademonitor.entity.UserEntity user = userService.getUserById(userDetails.getUserEntity().getId())
                .orElse(userDetails.getUserEntity());
        
        model.addAttribute("currentUser", user);
        
        // Find allowed accounts
        java.util.List<de.trademonitor.model.Account> allowedAccounts = new java.util.ArrayList<>();
        if ("ROLE_ADMIN".equals(user.getRole())) {
            allowedAccounts.addAll(accountManager.getAllAccounts());
        } else {
            java.util.Set<Long> allowedIds = user.getAllowedAccountIds();
            if (allowedIds != null) {
                for (Long id : allowedIds) {
                    de.trademonitor.model.Account acc = accountManager.getAccount(id);
                    if (acc != null) {
                        allowedAccounts.add(acc);
                    }
                }
            }
        }
        
        // Sort accounts by account ID or name
        allowedAccounts.sort(java.util.Comparator.comparing(de.trademonitor.model.Account::getAccountId));
        
        model.addAttribute("accounts", allowedAccounts);
        model.addAttribute("realAccountIds", user.getRealAccountIds());
        model.addAttribute("demoAccountIds", user.getDemoAccountIds());
        
        return "app-config";
    }

    @PostMapping("/app-config/save")
    public String saveAppConfig(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            jakarta.servlet.http.HttpServletRequest request,
            RedirectAttributes redirectAttrs) {
        
        if (userDetails == null) {
            return "redirect:/login";
        }
        
        de.trademonitor.entity.UserEntity user = userService.getUserById(userDetails.getUserEntity().getId())
                .orElse(userDetails.getUserEntity());
        
        try {
            java.util.Set<Long> allowedIds = new java.util.HashSet<>();
            if ("ROLE_ADMIN".equals(user.getRole())) {
                for (de.trademonitor.model.Account acc : accountManager.getAllAccounts()) {
                    allowedIds.add(acc.getAccountId());
                }
            } else if (user.getAllowedAccountIds() != null) {
                allowedIds.addAll(user.getAllowedAccountIds());
            }
            
            java.util.Set<Long> forcedReal = new java.util.HashSet<>();
            java.util.Set<Long> forcedDemo = new java.util.HashSet<>();
            
            for (Long accountId : allowedIds) {
                String val = request.getParameter("account_" + accountId);
                if ("REAL".equals(val)) {
                    forcedReal.add(accountId);
                } else if ("DEMO".equals(val)) {
                    forcedDemo.add(accountId);
                }
            }
            
            userService.updateAppAccountTypes(user.getId(), forcedReal, forcedDemo);
            
            redirectAttrs.addFlashAttribute("successMessage", "Konfiguration erfolgreich gespeichert!");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", "Fehler beim Speichern: " + e.getMessage());
        }
        
        return "redirect:/profile/app-config";
    }
}
