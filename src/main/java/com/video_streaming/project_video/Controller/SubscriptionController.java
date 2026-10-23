package com.video_streaming.project_video.Controller;

import com.video_streaming.project_video.DTOs.SubscriptionStatusDTO;
import com.video_streaming.project_video.DTOs.VideoDTO;
import com.video_streaming.project_video.Service.SubscriptionService;
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
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/api/channel/{channelId}/subscribe")
    public ResponseEntity<SubscriptionStatusDTO> toggleSubscribe(@PathVariable String channelId) {
        String subscriberUserId = SecurityUtils.getCurrentUserId();
        SubscriptionStatusDTO status = subscriptionService.toggleSubscribe(subscriberUserId, channelId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/api/channel/{channelId}/status")
    public ResponseEntity<SubscriptionStatusDTO> getSubscriptionStatus(@PathVariable String channelId) {
        String subscriberUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        SubscriptionStatusDTO status = subscriptionService.getSubscriptionStatus(subscriberUserId, channelId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/api/channel/{channelId}/subscribers")
    public ResponseEntity<Map<String, Object>> getSubscriberCount(@PathVariable String channelId) {
        long count = subscriptionService.getSubscriberCount(channelId);
        return ResponseEntity.ok(Map.of(
                "channelId", channelId,
                "subscriberCount", count
        ));
    }

    @GetMapping("/api/feed/subscriptions")
    public ResponseEntity<Page<VideoDTO>> getSubscriptionFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String subscriberUserId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        Page<VideoDTO> feed = subscriptionService.getSubscriptionFeed(subscriberUserId, pageable);
        return ResponseEntity.ok(feed);
    }
}
