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
public class CommentDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long commentId;
    private Long videoId;
    private UserDTO user;
    private Long parentCommentId;
    private String content;
    private Integer timestampInSeconds;
    private Long likeCount;
    private boolean isPinned;
    private long replyCount;
    private Date createdAt;
    private Date updatedAt;
}
