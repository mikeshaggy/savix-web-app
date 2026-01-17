package com.mikeshaggy.backend.common.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUserProvider {

    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found in security context");
        }
        
        Object principal = authentication.getPrincipal();
        
        if (principal instanceof String userIdString) {
            return UUID.fromString(userIdString);
        }
        
        throw new IllegalStateException("Unable to extract user ID from authentication principal");
    }
}
