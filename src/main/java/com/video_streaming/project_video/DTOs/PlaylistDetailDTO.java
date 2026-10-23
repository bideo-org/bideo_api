package com.video_streaming.project_video.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlaylistDetailDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long playlistId;
    private String title;
    private String description;
    private String userId;
    private String visibility;
    private boolean isSystem;
    private long videoCount;
    private List<VideoDTO> videos;
    private Date createdAt;
    private Date updatedAt;
}
