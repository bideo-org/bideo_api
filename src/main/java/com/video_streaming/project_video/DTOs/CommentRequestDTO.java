package com.video_streaming.project_video.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommentRequestDTO {

    @NotBlank(message = "Comment content cannot be blank")
    @Size(max = 2000, message = "Comment cannot exceed 2000 characters")
    private String content;

    private Long parentCommentId; // Optional: provided when replying to another comment

    private Integer timestampInSeconds; // Optional: video timestamp in seconds (e.g. 75 for 01:15)
}
