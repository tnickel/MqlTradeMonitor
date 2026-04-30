package de.trademonitor.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.Map;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request) {
        String originalUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        // API Requests: Return JSON
        if (originalUri != null && (originalUri.startsWith("/api/") || originalUri.startsWith("/admin/api/"))) {
            int statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
            if (status != null) {
                try {
                    statusCode = Integer.parseInt(status.toString());
                } catch (NumberFormatException ignored) {}
            }
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("error", "Unexpected Error");
            errorDetails.put("status", statusCode);
            errorDetails.put("path", originalUri);
            return ResponseEntity.status(statusCode).body(errorDetails);
        }

        // Web Requests: Redirect to Login Page
        return "redirect:/login";
    }
}
