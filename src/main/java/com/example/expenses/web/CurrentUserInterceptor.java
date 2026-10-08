package com.example.expenses.web;

import com.example.expenses.security.AppUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Puts the signed-in username in a request attribute so the shared layout can show it
 * on every page, including error pages.
 */
public class CurrentUserInterceptor implements HandlerInterceptor {

    static final String ATTRIBUTE = "currentUsername";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserDetails user) {
            request.setAttribute(ATTRIBUTE, user.getUsername());
        }
        return true;
    }
}
