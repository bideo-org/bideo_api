package com.video_streaming.project_video.Controller;

import com.video_streaming.project_video.DTOs.CommentDTO;
import com.video_streaming.project_video.DTOs.CommentRequestDTO;
import com.video_streaming.project_video.Service.CommentService;
import com.video_streaming.project_video.Utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/vid")
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/{videoId}/comments")
    public ResponseEntity<Page<CommentDTO>> getComments(
            @PathVariable Long videoId,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CommentDTO> comments = commentService.getComments(videoId, sort, pageable);
        return ResponseEntity.ok(comments);
    }

    @GetMapping("/comments/{commentId}/replies")
    public ResponseEntity<List<CommentDTO>> getReplies(@PathVariable Long commentId) {
        List<CommentDTO> replies = commentService.getReplies(commentId);
        return ResponseEntity.ok(replies);
    }

    @PostMapping("/{videoId}/comments")
    public ResponseEntity<CommentDTO> addComment(
            @PathVariable Long videoId,
            @Valid @RequestBody CommentRequestDTO request) {
        String userId = SecurityUtils.getCurrentUserId();
        CommentDTO comment = commentService.addComment(videoId, userId, request);
        return ResponseEntity.ok(comment);
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<CommentDTO> updateComment(
            @PathVariable Long commentId,
            @RequestBody Map<String, String> body) {
        String userId = SecurityUtils.getCurrentUserId();
        String content = body != null ? body.get("content") : null;
        CommentDTO updated = commentService.updateComment(commentId, userId, content);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Map<String, String>> deleteComment(@PathVariable Long commentId) {
        String userId = SecurityUtils.getCurrentUserId();
        commentService.deleteComment(commentId, userId);
        return ResponseEntity.ok(Map.of("message", "Comment deleted successfully", "commentId", String.valueOf(commentId)));
    }

    @PostMapping("/comments/{commentId}/pin")
    public ResponseEntity<CommentDTO> togglePin(@PathVariable Long commentId) {
        String userId = SecurityUtils.getCurrentUserId();
        CommentDTO pinned = commentService.togglePinComment(commentId, userId);
        return ResponseEntity.ok(pinned);
    }
}
