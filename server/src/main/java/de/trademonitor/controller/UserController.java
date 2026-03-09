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
}
