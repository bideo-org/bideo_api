package com.video_streaming.project_video.Controller;

import com.video_streaming.project_video.DTOs.SystemHealthDTO;
import com.video_streaming.project_video.Service.SystemHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class HealthController {

    private final SystemHealthService healthService;

    @GetMapping("/health")
    public ResponseEntity<SystemHealthDTO> getHealth() {
        return ResponseEntity.ok(healthService.getSystemHealth());
    }
}
