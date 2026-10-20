package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.DTOs.SystemHealthDTO;

public interface SystemHealthService {

    /**
     * Inspects all backing services (PostgreSQL, Redis, RabbitMQ, Firebase, AWS S3)
     * and returns a detailed status breakdown with measured latencies.
     *
     * @return SystemHealthDTO containing overall status and per-service health metrics.
     */
    SystemHealthDTO getSystemHealth();
}
