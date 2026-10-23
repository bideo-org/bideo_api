package com.video_streaming.project_video.Repository;

import com.video_streaming.project_video.Entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Top-level comments (parentCommentId IS NULL) sorted by pinned first, then creation date desc
    Page<Comment> findByVideo_VideoIdAndParentCommentIdIsNullOrderByIsPinnedDescCreatedAtDesc(
            Long videoId,
            Pageable pageable
    );

    // Top-level comments sorted by pinned first, then like count desc, then creation date desc
    Page<Comment> findByVideo_VideoIdAndParentCommentIdIsNullOrderByIsPinnedDescLikeCountDescCreatedAtDesc(
            Long videoId,
            Pageable pageable
    );

    // Replies to a specific parent comment
    List<Comment> findByParentCommentIdOrderByCreatedAtAsc(Long parentCommentId);

    long countByParentCommentId(Long parentCommentId);

    long countByVideo_VideoId(Long videoId);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.parentCommentId = :parentCommentId")
    void deleteByParentCommentId(@Param("parentCommentId") Long parentCommentId);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.video.videoId = :videoId")
    void deleteByVideoId(@Param("videoId") Long videoId);
}
