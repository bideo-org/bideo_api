package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.DTOs.CommentDTO;
import com.video_streaming.project_video.DTOs.CommentRequestDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CommentService {

    CommentDTO addComment(Long videoId, String userId, CommentRequestDTO request);

    Page<CommentDTO> getComments(Long videoId, String sort, Pageable pageable);

    List<CommentDTO> getReplies(Long parentCommentId);

    CommentDTO updateComment(Long commentId, String userId, String newContent);

    void deleteComment(Long commentId, String userId);

    CommentDTO togglePinComment(Long commentId, String userId);
}
