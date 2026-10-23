package com.video_streaming.project_video.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WatchProgressDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long videoId;
    private String videoTitle;
    private String slug;
    private String thumbnailUrl;
    private String channelName;
    private String channelHandle;
    private String channelAvatar;
    private Double progressSeconds;
    private Double durationSeconds;
    private boolean completed;
    private Date lastWatchedAt;
}
