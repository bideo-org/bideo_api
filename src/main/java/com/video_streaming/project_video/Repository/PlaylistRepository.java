package com.video_streaming.project_video.Repository;

import com.video_streaming.project_video.Entity.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    List<Playlist> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Playlist> findByUserIdAndIsSystem(String userId, boolean isSystem);

    Optional<Playlist> findByPlaylistIdAndUserId(Long playlistId, String userId);

    @Modifying
    @Query("DELETE FROM Playlist p WHERE p.playlistId = :playlistId AND p.userId = :userId")
    void deleteByPlaylistIdAndUserId(@Param("playlistId") Long playlistId, @Param("userId") String userId);
}
