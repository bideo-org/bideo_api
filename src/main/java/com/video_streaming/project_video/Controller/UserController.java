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

    @PostMapping(value = "/update")
    public ResponseEntity<String> updateUser(
            @RequestBody(required = false) UserDTO bodyDTO,
            @ModelAttribute UserDTO formDTO
    ) {
        try {
            UserDTO targetDTO = (bodyDTO != null && bodyDTO.getUserId() != null) ? bodyDTO : formDTO;
            String response = userService.updateUserProfile(targetDTO);
            logger.info("Updated profile for user: {}", targetDTO.getUserId());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Validation error updating user: {}", e.getMessage());
            return ResponseEntity.status(400).body("Validation error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error updating user: {}", e.getMessage());
            return ResponseEntity.status(500).body("Error updating user: " + e.getMessage());
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
