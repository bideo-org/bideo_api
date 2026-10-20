package com.video_streaming.project_video.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemHealthDTO {
    private String status; // "UP", "DEGRADED", "DOWN"
    private String timestamp;
    private long uptimeSeconds;
    private String version;
    private Map<String, ServiceStatusDTO> services;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceStatusDTO {
        private String status; // "UP", "DOWN", "UNCONFIGURED"
        private Long latencyMs;
        private String message;
    }
}
