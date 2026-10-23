package com.video_streaming.project_video.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VideoEngagementDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long videoId;
    private Long likesCount;
    private Long dislikesCount;
    private String userReaction; // "LIKE", "DISLIKE", "NONE"
}
