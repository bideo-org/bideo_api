package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.DTOs.PlaylistDTO;
import com.video_streaming.project_video.DTOs.PlaylistDetailDTO;

import java.util.List;

public interface PlaylistService {

    PlaylistDTO createPlaylist(String userId, String title, String description, String visibility);

    List<PlaylistDTO> getUserPlaylists(String userId);

    PlaylistDetailDTO getPlaylistDetails(Long playlistId, String requestingUserId);

    PlaylistDetailDTO addVideoToPlaylist(Long playlistId, Long videoId, String userId);

    PlaylistDetailDTO removeVideoFromPlaylist(Long playlistId, Long videoId, String userId);

    void deletePlaylist(Long playlistId, String userId);

    PlaylistDTO getOrCreateWatchLater(String userId);

    PlaylistDetailDTO toggleWatchLater(Long videoId, String userId);
}
