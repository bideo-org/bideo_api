package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.DTOs.VideoEngagementDTO;

public interface EngagementService {

    VideoEngagementDTO toggleLike(Long videoId, String userId);

    VideoEngagementDTO toggleDislike(Long videoId, String userId);

    VideoEngagementDTO getEngagement(Long videoId, String userId);

    Long getLikesCount(Long videoId, Long fallbackDbCount);

    Long getDislikesCount(Long videoId, Long fallbackDbCount);

    void flushPendingEngagementToDatabase();
}
