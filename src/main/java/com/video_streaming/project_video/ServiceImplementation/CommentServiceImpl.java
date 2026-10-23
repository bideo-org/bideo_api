package com.video_streaming.project_video.ServiceImplementation;

import com.video_streaming.project_video.DTOMapper.UserDTOMapper;
import com.video_streaming.project_video.DTOs.CommentDTO;
import com.video_streaming.project_video.DTOs.CommentRequestDTO;
import com.video_streaming.project_video.Entity.Comment;
import com.video_streaming.project_video.Entity.User;
import com.video_streaming.project_video.Entity.Video;
import com.video_streaming.project_video.Repository.CommentRepository;
import com.video_streaming.project_video.Repository.UserRepository;
import com.video_streaming.project_video.Repository.VideoRepository;
import com.video_streaming.project_video.Service.CommentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private static final Logger logger = LoggerFactory.getLogger(CommentServiceImpl.class);
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})");

    private final CommentRepository commentRepository;
    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final UserDTOMapper userDTOMapper = new UserDTOMapper();

    @Override
    @Transactional
    public CommentDTO addComment(Long videoId, String userId, CommentRequestDTO request) {
        if (videoId == null || userId == null || userId.isBlank() || request == null) {
            throw new IllegalArgumentException("Video ID, User ID, and comment payload are required");
        }

        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found with ID: " + videoId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        Long targetParentId = null;
        Long requestedParentId = request.getParentCommentId();
        if (requestedParentId != null) {
            Comment parent = commentRepository.findById(requestedParentId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent comment not found with ID: " + requestedParentId));
            if (!parent.getVideo().getVideoId().equals(videoId)) {
                throw new IllegalArgumentException("Parent comment does not belong to this video");
            }
            // Keep threads shallow (2-tier): if replying to a reply, attach to the root comment
            targetParentId = parent.getParentCommentId() != null ? parent.getParentCommentId() : requestedParentId;
        }

        Integer timestamp = request.getTimestampInSeconds();
        if (timestamp == null && request.getContent() != null) {
            timestamp = extractFirstTimestampInSeconds(request.getContent());
        }

        Comment comment = Comment.builder()
                .video(video)
                .user(user)
                .parentCommentId(targetParentId)
                .content(request.getContent().trim())
                .timestampInSeconds(timestamp)
                .likeCount(0L)
                .isPinned(false)
                .createdAt(new Date())
                .build();

        Comment saved = commentRepository.save(comment);
        logger.info("Added comment ID: {} on video ID: {} by user: {}", saved.getCommentId(), videoId, userId);

        return toDTO(saved, 0L);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CommentDTO> getComments(Long videoId, String sort, Pageable pageable) {
        if (videoId == null) {
            throw new IllegalArgumentException("Video ID is required");
        }

        Page<Comment> commentPage;
        if ("top".equalsIgnoreCase(sort)) {
            commentPage = commentRepository
                    .findByVideo_VideoIdAndParentCommentIdIsNullOrderByIsPinnedDescLikeCountDescCreatedAtDesc(videoId, pageable);
        } else {
            commentPage = commentRepository
                    .findByVideo_VideoIdAndParentCommentIdIsNullOrderByIsPinnedDescCreatedAtDesc(videoId, pageable);
        }

        return commentPage.map(c -> {
            long replies = commentRepository.countByParentCommentId(c.getCommentId());
            return toDTO(c, replies);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentDTO> getReplies(Long parentCommentId) {
        if (parentCommentId == null) {
            throw new IllegalArgumentException("Parent comment ID is required");
        }

        List<Comment> replies = commentRepository.findByParentCommentIdOrderByCreatedAtAsc(parentCommentId);
        return replies.stream().map(c -> toDTO(c, 0L)).toList();
    }

    @Override
    @Transactional
    public CommentDTO updateComment(Long commentId, String userId, String newContent) {
        if (commentId == null || userId == null || newContent == null || newContent.isBlank()) {
            throw new IllegalArgumentException("Comment ID, User ID, and content are required");
        }

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found with ID: " + commentId));

        if (!comment.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException("Unauthorized: Only the comment author can edit this comment");
        }

        comment.setContent(newContent.trim());
        comment.setUpdatedAt(new Date());

        Integer timestamp = extractFirstTimestampInSeconds(newContent);
        if (timestamp != null) {
            comment.setTimestampInSeconds(timestamp);
        }

        Comment updated = commentRepository.save(comment);
        long replies = commentRepository.countByParentCommentId(commentId);
        return toDTO(updated, replies);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, String userId) {
        if (commentId == null || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Comment ID and User ID are required");
        }

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found with ID: " + commentId));

        boolean isAuthor = comment.getUser().getUserId().equals(userId);
        boolean isVideoOwner = comment.getVideo().getVideo_uploader() != null &&
                comment.getVideo().getVideo_uploader().getUserId().equals(userId);

        if (!isAuthor && !isVideoOwner) {
            throw new AccessDeniedException("Unauthorized: Only the author or video creator can delete this comment");
        }

        // Delete any child replies first
        commentRepository.deleteByParentCommentId(commentId);
        commentRepository.delete(comment);
        logger.info("Deleted comment ID: {} by user: {}", commentId, userId);
    }

    @Override
    @Transactional
    public CommentDTO togglePinComment(Long commentId, String userId) {
        if (commentId == null || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Comment ID and User ID are required");
        }

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found with ID: " + commentId));

        boolean isVideoOwner = comment.getVideo().getVideo_uploader() != null &&
                comment.getVideo().getVideo_uploader().getUserId().equals(userId);

        if (!isVideoOwner) {
            throw new AccessDeniedException("Unauthorized: Only the video creator can pin comments");
        }

        comment.setPinned(!comment.isPinned());
        Comment saved = commentRepository.save(comment);
        long replies = commentRepository.countByParentCommentId(commentId);
        return toDTO(saved, replies);
    }

    private CommentDTO toDTO(Comment comment, long replyCount) {
        return CommentDTO.builder()
                .commentId(comment.getCommentId())
                .videoId(comment.getVideo().getVideoId())
                .user(userDTOMapper.convertEntityToDTO(comment.getUser()))
                .parentCommentId(comment.getParentCommentId())
                .content(comment.getContent())
                .timestampInSeconds(comment.getTimestampInSeconds())
                .likeCount(comment.getLikeCount())
                .isPinned(comment.isPinned())
                .replyCount(replyCount)
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    private Integer extractFirstTimestampInSeconds(String text) {
        if (text == null) return null;
        Matcher matcher = TIMESTAMP_PATTERN.matcher(text);
        if (matcher.find()) {
            String hoursStr = matcher.group(1);
            String minsStr = matcher.group(2);
            String secsStr = matcher.group(3);

            int hours = hoursStr != null ? Integer.parseInt(hoursStr) : 0;
            int mins = Integer.parseInt(minsStr);
            int secs = Integer.parseInt(secsStr);
            return (hours * 3600) + (mins * 60) + secs;
        }
        return null;
    }
}
