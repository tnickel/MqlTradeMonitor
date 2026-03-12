package de.trademonitor.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.lang.NonNull;

@Component
public class DeviceDetectionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, 
                             @NonNull HttpServletResponse response, 
                             @NonNull Object handler) throws Exception {
        
        String userAgent = request.getHeader("User-Agent");
        boolean isMobile = false;

        if (userAgent != null) {
            String ua = userAgent.toLowerCase();
            // Basic mobile detection logic
            if (ua.contains("mobi") || ua.contains("android") || ua.contains("iphone") || ua.contains("ipad")) {
                isMobile = true;
            }
        }

        request.setAttribute("isMobile", isMobile);
        return true;
    }
}
