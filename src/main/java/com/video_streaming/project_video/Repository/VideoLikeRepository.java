package com.video_streaming.project_video.Repository;

import com.video_streaming.project_video.Entity.VideoLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VideoLikeRepository extends JpaRepository<VideoLike, Long> {

    Optional<VideoLike> findByVideoIdAndUserId(Long videoId, String userId);

    java.util.List<VideoLike> findByVideoId(Long videoId);

    @Query("SELECT COUNT(vl) FROM VideoLike vl WHERE vl.videoId = :videoId AND vl.isLike = :isLike")
    long countByVideoIdAndIsLike(@Param("videoId") Long videoId, @Param("isLike") boolean isLike);

    @Query("SELECT vl FROM VideoLike vl WHERE vl.userId = :userId AND vl.isLike = :isLike ORDER BY vl.createdAt DESC")
    Page<VideoLike> findByUserIdAndIsLikeOrderByCreatedAtDesc(@Param("userId") String userId, @Param("isLike") boolean isLike, Pageable pageable);

    @Modifying
    @Query("DELETE FROM VideoLike vl WHERE vl.videoId = :videoId AND vl.userId = :userId")
    void deleteByVideoIdAndUserId(@Param("videoId") Long videoId, @Param("userId") String userId);

    @Modifying
    @Query("DELETE FROM VideoLike vl WHERE vl.videoId = :videoId")
    void deleteByVideoId(@Param("videoId") Long videoId);
}
