package com.video_streaming.project_video.Controller;

import com.video_streaming.project_video.DTOs.PlaylistDTO;
import com.video_streaming.project_video.DTOs.PlaylistDetailDTO;
import com.video_streaming.project_video.Service.PlaylistService;
import com.video_streaming.project_video.Utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playlists")
public class PlaylistController {

    private final PlaylistService playlistService;

    @PostMapping
    public ResponseEntity<PlaylistDTO> createPlaylist(@RequestBody Map<String, String> body) {
        String userId = SecurityUtils.getCurrentUserId();
        String title = body != null ? body.get("title") : null;
        String description = body != null ? body.get("description") : null;
        String visibility = body != null ? body.get("visibility") : "PUBLIC";

        PlaylistDTO playlist = playlistService.createPlaylist(userId, title, description, visibility);
        return ResponseEntity.ok(playlist);
    }

    @GetMapping("/my")
    public ResponseEntity<List<PlaylistDTO>> getMyPlaylists() {
        String userId = SecurityUtils.getCurrentUserId();
        List<PlaylistDTO> playlists = playlistService.getUserPlaylists(userId);
        return ResponseEntity.ok(playlists);
    }

    @GetMapping("/{playlistId}")
    public ResponseEntity<PlaylistDetailDTO> getPlaylistDetails(@PathVariable Long playlistId) {
        String userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        PlaylistDetailDTO details = playlistService.getPlaylistDetails(playlistId, userId);
        return ResponseEntity.ok(details);
    }

    @PostMapping("/{playlistId}/videos/{videoId}")
    public ResponseEntity<PlaylistDetailDTO> addVideoToPlaylist(
            @PathVariable Long playlistId,
            @PathVariable Long videoId) {
        String userId = SecurityUtils.getCurrentUserId();
        PlaylistDetailDTO updated = playlistService.addVideoToPlaylist(playlistId, videoId, userId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{playlistId}/videos/{videoId}")
    public ResponseEntity<PlaylistDetailDTO> removeVideoFromPlaylist(
            @PathVariable Long playlistId,
            @PathVariable Long videoId) {
        String userId = SecurityUtils.getCurrentUserId();
        PlaylistDetailDTO updated = playlistService.removeVideoFromPlaylist(playlistId, videoId, userId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{playlistId}")
    public ResponseEntity<Map<String, String>> deletePlaylist(@PathVariable Long playlistId) {
        String userId = SecurityUtils.getCurrentUserId();
        playlistService.deletePlaylist(playlistId, userId);
        return ResponseEntity.ok(Map.of("message", "Playlist deleted successfully", "playlistId", String.valueOf(playlistId)));
    }

    @PostMapping("/watch-later/{videoId}")
    public ResponseEntity<PlaylistDetailDTO> toggleWatchLater(@PathVariable Long videoId) {
        String userId = SecurityUtils.getCurrentUserId();
        PlaylistDetailDTO updated = playlistService.toggleWatchLater(videoId, userId);
        return ResponseEntity.ok(updated);
    }
}
