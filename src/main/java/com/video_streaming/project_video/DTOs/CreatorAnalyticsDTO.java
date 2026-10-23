package com.video_streaming.project_video.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreatorAnalyticsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String creatorUserId;
    private long totalVideos;
    private long totalViews;
    private long totalLikes;
    private long totalSubscribers;
    private List<VideoDTO> topVideos;
}
