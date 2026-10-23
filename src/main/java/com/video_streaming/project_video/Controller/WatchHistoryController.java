package com.video_streaming.project_video.Controller;

import com.video_streaming.project_video.DTOs.WatchProgressDTO;
import com.video_streaming.project_video.Service.WatchHistoryService;
import com.video_streaming.project_video.Utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class WatchHistoryController {

    private final WatchHistoryService watchHistoryService;

    @PostMapping("/api/vid/{videoId}/progress")
    public ResponseEntity<WatchProgressDTO> recordProgress(
            @PathVariable Long videoId,
            @RequestParam("seconds") Double seconds,
            @RequestParam(value = "duration", required = false) Double duration) {
        String userId = SecurityUtils.getCurrentUserId();
        WatchProgressDTO progress = watchHistoryService.recordProgress(videoId, userId, seconds, duration);
        return ResponseEntity.ok(progress);
    }

    @GetMapping("/api/vid/{videoId}/progress")
    public ResponseEntity<WatchProgressDTO> getProgress(@PathVariable Long videoId) {
        String userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        WatchProgressDTO progress = watchHistoryService.getProgress(videoId, userId);
        return ResponseEntity.ok(progress);
    }

    @GetMapping("/api/user/history")
    public ResponseEntity<Page<WatchProgressDTO>> getUserHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        Page<WatchProgressDTO> history = watchHistoryService.getUserHistory(userId, pageable);
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/api/user/history/{videoId}")
    public ResponseEntity<Map<String, String>> deleteHistoryItem(@PathVariable Long videoId) {
        String userId = SecurityUtils.getCurrentUserId();
        watchHistoryService.deleteHistoryItem(videoId, userId);
        return ResponseEntity.ok(Map.of("message", "History item removed", "videoId", String.valueOf(videoId)));
    }

    @DeleteMapping("/api/user/history")
    public ResponseEntity<Map<String, String>> clearAllHistory() {
        String userId = SecurityUtils.getCurrentUserId();
        watchHistoryService.clearAllHistory(userId);
        return ResponseEntity.ok(Map.of("message", "Watch history cleared successfully"));
    }
}
