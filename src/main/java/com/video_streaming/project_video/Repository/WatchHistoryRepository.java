package com.video_streaming.project_video.Repository;

import com.video_streaming.project_video.Entity.WatchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {

    Optional<WatchHistory> findByUserIdAndVideo_VideoId(String userId, Long videoId);

    Page<WatchHistory> findByUserIdOrderByLastWatchedAtDesc(String userId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM WatchHistory wh WHERE wh.userId = :userId AND wh.video.videoId = :videoId")
    void deleteByUserIdAndVideoId(@Param("userId") String userId, @Param("videoId") Long videoId);

    @Modifying
    @Query("DELETE FROM WatchHistory wh WHERE wh.userId = :userId")
    void deleteAllByUserId(@Param("userId") String userId);

    @Modifying
    @Query("DELETE FROM WatchHistory wh WHERE wh.video.videoId = :videoId")
    void deleteByVideoId(@Param("videoId") Long videoId);
}
