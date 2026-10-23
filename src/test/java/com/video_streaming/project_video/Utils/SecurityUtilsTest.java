package com.video_streaming.project_video.Utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should extract authenticated Firebase UID from SecurityContext")
    void testGetCurrentUserId_authenticated() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("firebase-uid-12345", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        String uid = SecurityUtils.getCurrentUserId();
        assertEquals("firebase-uid-12345", uid);

        Optional<String> optionalUid = SecurityUtils.getCurrentUserIdOptional();
        assertTrue(optionalUid.isPresent());
        assertEquals("firebase-uid-12345", optionalUid.get());
    }

    @Test
    @DisplayName("Should throw SecurityException when calling getCurrentUserId without authentication")
    void testGetCurrentUserId_unauthenticated() {
        SecurityContextHolder.clearContext();

        assertThrows(SecurityException.class, SecurityUtils::getCurrentUserId);

        Optional<String> optionalUid = SecurityUtils.getCurrentUserIdOptional();
        assertTrue(optionalUid.isEmpty());
    }

    @Test
    @DisplayName("Should treat anonymousUser as unauthenticated")
    void testGetCurrentUserId_anonymousUser() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("anonymousUser", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(SecurityException.class, SecurityUtils::getCurrentUserId);
        assertTrue(SecurityUtils.getCurrentUserIdOptional().isEmpty());
    }
}
