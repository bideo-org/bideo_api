package com.video_streaming.project_video.Utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * Retrieves the authenticated user's Firebase UID from the SecurityContext.
     * Throws an IllegalArgumentException or SecurityException if unauthenticated.
     */
    public static String getCurrentUserId() {
        return getCurrentUserIdOptional()
                .orElseThrow(() -> new SecurityException("User is not authenticated"));
    }

    /**
     * Optionally retrieves the authenticated user's Firebase UID.
     * Returns Optional.empty() if caller is anonymous or unauthenticated.
     */
    public static Optional<String> getCurrentUserIdOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof String uid && !uid.isBlank() && !"anonymousUser".equalsIgnoreCase(uid)) {
            return Optional.of(uid);
        }

        String name = auth.getName();
        if (name != null && !name.isBlank() && !"anonymousUser".equalsIgnoreCase(name)) {
            return Optional.of(name);
        }

        return Optional.empty();
    }
}
