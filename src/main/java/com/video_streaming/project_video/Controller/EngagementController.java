package com.video_streaming.project_video.Controller;

import com.video_streaming.project_video.DTOs.VideoEngagementDTO;
import com.video_streaming.project_video.Service.EngagementService;
import com.video_streaming.project_video.Utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/vid")
public class EngagementController {

    private final EngagementService engagementService;

    @PostMapping("/{videoId}/like")
    public ResponseEntity<VideoEngagementDTO> toggleLike(@PathVariable Long videoId) {
        String userId = SecurityUtils.getCurrentUserId();
        VideoEngagementDTO response = engagementService.toggleLike(videoId, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{videoId}/dislike")
    public ResponseEntity<VideoEngagementDTO> toggleDislike(@PathVariable Long videoId) {
        String userId = SecurityUtils.getCurrentUserId();
        VideoEngagementDTO response = engagementService.toggleDislike(videoId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{videoId}/engagement")
    public ResponseEntity<VideoEngagementDTO> getEngagement(@PathVariable Long videoId) {
        String userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        VideoEngagementDTO response = engagementService.getEngagement(videoId, userId);
        return ResponseEntity.ok(response);
    }
}
