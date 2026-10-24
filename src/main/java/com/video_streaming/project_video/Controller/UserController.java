package com.video_streaming.project_video.Controller;

import com.video_streaming.project_video.DTOs.UserDTO;
import com.video_streaming.project_video.Service.UserService;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;

    /**
     * Handles JSON updates: application/json
     */
    @RequestMapping(
            value = {"/update", "", "/updateProfile", "/{id}", "/update/{id}"},
            method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH},
            consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> updateUserJson(
            @RequestBody(required = false) UserDTO bodyDTO,
            @PathVariable(value = "id", required = false) String pathId,
            @RequestParam(value = "userId", required = false) String queryUserId
    ) {
        return processUpdateUser(bodyDTO, pathId, queryUserId);
    }

    /**
     * Handles multipart/form-data and form-urlencoded updates (e.g., FormData sent from browser)
     */
    @RequestMapping(
            value = {"/update", "", "/updateProfile", "/{id}", "/update/{id}"},
            method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH},
            consumes = {org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE, org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE}
    )
    public ResponseEntity<String> updateUserFormData(
            @ModelAttribute UserDTO formDTO,
            @PathVariable(value = "id", required = false) String pathId,
            @RequestParam(value = "userId", required = false) String queryUserId
    ) {
        return processUpdateUser(formDTO, pathId, queryUserId);
    }

    /**
     * Fallback for requests without explicit Content-Type header
     */
    @RequestMapping(
            value = {"/update", "", "/updateProfile", "/{id}", "/update/{id}"},
            method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH}
    )
    public ResponseEntity<String> updateUserFallback(
            @ModelAttribute UserDTO formDTO,
            @PathVariable(value = "id", required = false) String pathId,
            @RequestParam(value = "userId", required = false) String queryUserId
    ) {
        return processUpdateUser(formDTO, pathId, queryUserId);
    }

    private ResponseEntity<String> processUpdateUser(UserDTO dto, String pathId, String queryUserId) {
        try {
            if (dto == null) {
                dto = new UserDTO();
            }

            // 1. Resolve userId from DTO, path variable, query param, or SecurityContext
            String resolvedUserId = dto.getUserId();
            if (resolvedUserId == null || resolvedUserId.isBlank()) {
                if (pathId != null && !pathId.isBlank()) {
                    resolvedUserId = pathId;
                } else if (queryUserId != null && !queryUserId.isBlank()) {
                    resolvedUserId = queryUserId;
                } else {
                    resolvedUserId = com.video_streaming.project_video.Utils.SecurityUtils.getCurrentUserIdOptional().orElse(null);
                }
            }

            if (resolvedUserId == null || resolvedUserId.isBlank()) {
                logger.warn("User ID not found in payload, parameters, or SecurityContext");
                return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .body("Validation error: User ID is required to update profile");
            }
            dto.setUserId(resolvedUserId);

            // 2. Prevent updating another user's profile if authenticated
            var currentUserIdOpt = com.video_streaming.project_video.Utils.SecurityUtils.getCurrentUserIdOptional();
            if (currentUserIdOpt.isPresent()) {
                String authenticatedUid = currentUserIdOpt.get();
                if (!authenticatedUid.equalsIgnoreCase(resolvedUserId)) {
                    try {
                        UserDTO target = userService.getUserById(resolvedUserId);
                        if (target != null && !authenticatedUid.equalsIgnoreCase(target.getUserId())) {
                            logger.warn("Caller {} attempted to update profile of {}", authenticatedUid, resolvedUserId);
                            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                                    .body("Forbidden: Cannot update another user's profile");
                        }
                    } catch (Exception ignored) {
                        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                                .body("Forbidden: Cannot update another user's profile");
                    }
                }
            }

            String response = userService.updateUserProfile(dto);
            logger.info("Updated profile for user: {}", dto.getUserId());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Validation error updating user: {}", e.getMessage());
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST).body("Validation error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error updating user: {}", e.getMessage(), e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating user: " + e.getMessage());
        }
    }

    @GetMapping("/getUser/{id}")
    public ResponseEntity<?> getUserById(@PathVariable("id") String userId) {
        try {
            UserDTO response = userService.getUserById(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/search")
    public ResponseEntity<java.util.List<UserDTO>> searchUsers(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(userService.searchUsers(query, limit));
    }
}
