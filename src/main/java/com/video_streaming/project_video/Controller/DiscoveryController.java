package com.video_streaming.project_video.Controller;

import com.video_streaming.project_video.DTOs.VideoDTO;
import com.video_streaming.project_video.Service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/vid")
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    @GetMapping("/{videoId}/related")
    public ResponseEntity<Page<VideoDTO>> getRelatedVideos(
            @PathVariable Long videoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<VideoDTO> related = discoveryService.getRelatedVideos(videoId, pageable);
        return ResponseEntity.ok(related);
    }

    @GetMapping("/trending")
    public ResponseEntity<Page<VideoDTO>> getTrendingVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<VideoDTO> trending = discoveryService.getTrendingVideos(pageable);
        return ResponseEntity.ok(trending);
    }

    @GetMapping("/search/suggest")
    public ResponseEntity<List<String>> getSearchSuggestions(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(defaultValue = "8") int limit) {
        List<String> suggestions = discoveryService.getSearchSuggestions(query, limit);
        return ResponseEntity.ok(suggestions);
    }
}
