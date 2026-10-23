package com.video_streaming.project_video.ServiceImplementation;

import com.video_streaming.project_video.DTOMapper.VideoDTOMapper;
import com.video_streaming.project_video.DTOs.PlaylistDTO;
import com.video_streaming.project_video.DTOs.PlaylistDetailDTO;
import com.video_streaming.project_video.DTOs.VideoDTO;
import com.video_streaming.project_video.Entity.Playlist;
import com.video_streaming.project_video.Entity.PlaylistVideo;
import com.video_streaming.project_video.Entity.Video;
import com.video_streaming.project_video.Repository.PlaylistRepository;
import com.video_streaming.project_video.Repository.PlaylistVideoRepository;
import com.video_streaming.project_video.Repository.VideoRepository;
import com.video_streaming.project_video.Service.PlaylistService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlaylistServiceImpl implements PlaylistService {

    private static final Logger logger = LoggerFactory.getLogger(PlaylistServiceImpl.class);

    private final PlaylistRepository playlistRepository;
    private final PlaylistVideoRepository playlistVideoRepository;
    private final VideoRepository videoRepository;
    private final VideoDTOMapper videoDTOMapper = new VideoDTOMapper();

    @Override
    @Transactional
    public PlaylistDTO createPlaylist(String userId, String title, String description, String visibility) {
        if (userId == null || userId.isBlank() || title == null || title.isBlank()) {
            throw new IllegalArgumentException("User ID and Playlist title are required");
        }

        String vis = (visibility != null && !visibility.isBlank()) ? visibility.trim().toUpperCase() : "PUBLIC";

        Playlist playlist = Playlist.builder()
                .userId(userId.trim())
                .title(title.trim())
                .description(description != null ? description.trim() : null)
                .visibility(vis)
                .isSystem(false)
                .createdAt(new Date())
                .build();

        Playlist saved = playlistRepository.save(playlist);
        logger.info("User {} created playlist ID: {}", userId, saved.getPlaylistId());

        return toDTO(saved, 0L);
    }

    @Override
    @Transactional
    public List<PlaylistDTO> getUserPlaylists(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        // Auto-provision Watch Later if it does not exist yet
        getOrCreateWatchLater(userId);

        List<Playlist> playlists = playlistRepository.findByUserIdOrderByCreatedAtDesc(userId.trim());
        return playlists.stream().map(p -> {
            long count = playlistVideoRepository.countByPlaylistId(p.getPlaylistId());
            return toDTO(p, count);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PlaylistDetailDTO getPlaylistDetails(Long playlistId, String requestingUserId) {
        if (playlistId == null) {
            throw new IllegalArgumentException("Playlist ID is required");
        }

        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new IllegalArgumentException("Playlist not found with ID: " + playlistId));

        if ("PRIVATE".equalsIgnoreCase(playlist.getVisibility())) {
            if (requestingUserId == null || !requestingUserId.trim().equals(playlist.getUserId())) {
                throw new AccessDeniedException("This playlist is private");
            }
        }

        List<PlaylistVideo> pvList = playlistVideoRepository.findByPlaylistIdOrderByPositionAsc(playlistId);
        List<VideoDTO> videos = pvList.stream()
                .map(pv -> videoDTOMapper.convertEntityToDTO(pv.getVideo()))
                .toList();

        return PlaylistDetailDTO.builder()
                .playlistId(playlist.getPlaylistId())
                .title(playlist.getTitle())
                .description(playlist.getDescription())
                .userId(playlist.getUserId())
                .visibility(playlist.getVisibility())
                .isSystem(playlist.isSystem())
                .videoCount(videos.size())
                .videos(videos)
                .createdAt(playlist.getCreatedAt())
                .updatedAt(playlist.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public PlaylistDetailDTO addVideoToPlaylist(Long playlistId, Long videoId, String userId) {
        if (playlistId == null || videoId == null || userId == null) {
            throw new IllegalArgumentException("Playlist ID, Video ID, and User ID are required");
        }

        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new IllegalArgumentException("Playlist not found with ID: " + playlistId));

        if (!playlist.getUserId().equals(userId.trim())) {
            throw new AccessDeniedException("Unauthorized: Only playlist owner can add videos");
        }

        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found with ID: " + videoId));

        if (!playlistVideoRepository.existsByPlaylistIdAndVideo_VideoId(playlistId, videoId)) {
            int position = (int) playlistVideoRepository.countByPlaylistId(playlistId) + 1;
            PlaylistVideo pv = PlaylistVideo.builder()
                    .playlistId(playlistId)
                    .video(video)
                    .position(position)
                    .addedAt(new Date())
                    .build();
            playlistVideoRepository.save(pv);
            playlist.setUpdatedAt(new Date());
            playlistRepository.save(playlist);
        }

        return getPlaylistDetails(playlistId, userId);
    }

    @Override
    @Transactional
    public PlaylistDetailDTO removeVideoFromPlaylist(Long playlistId, Long videoId, String userId) {
        if (playlistId == null || videoId == null || userId == null) {
            throw new IllegalArgumentException("Playlist ID, Video ID, and User ID are required");
        }

        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new IllegalArgumentException("Playlist not found with ID: " + playlistId));

        if (!playlist.getUserId().equals(userId.trim())) {
            throw new AccessDeniedException("Unauthorized: Only playlist owner can remove videos");
        }

        playlistVideoRepository.deleteByPlaylistIdAndVideoId(playlistId, videoId);
        playlist.setUpdatedAt(new Date());
        playlistRepository.save(playlist);

        return getPlaylistDetails(playlistId, userId);
    }

    @Override
    @Transactional
    public void deletePlaylist(Long playlistId, String userId) {
        if (playlistId == null || userId == null) {
            throw new IllegalArgumentException("Playlist ID and User ID are required");
        }

        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new IllegalArgumentException("Playlist not found with ID: " + playlistId));

        if (!playlist.getUserId().equals(userId.trim())) {
            throw new AccessDeniedException("Unauthorized: Only playlist owner can delete playlist");
        }

        if (playlist.isSystem()) {
            throw new IllegalArgumentException("System default playlists cannot be deleted");
        }

        playlistVideoRepository.deleteByPlaylistId(playlistId);
        playlistRepository.delete(playlist);
        logger.info("Deleted playlist ID: {} by user: {}", playlistId, userId);
    }

    @Override
    @Transactional
    public PlaylistDTO getOrCreateWatchLater(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        String uid = userId.trim();
        Optional<Playlist> existingOpt = playlistRepository.findByUserIdAndIsSystem(uid, true);

        Playlist watchLater = existingOpt.orElseGet(() -> {
            Playlist pl = Playlist.builder()
                    .userId(uid)
                    .title("Watch Later")
                    .description("Videos saved to watch later")
                    .visibility("PRIVATE")
                    .isSystem(true)
                    .createdAt(new Date())
                    .build();
            return playlistRepository.save(pl);
        });

        long count = playlistVideoRepository.countByPlaylistId(watchLater.getPlaylistId());
        return toDTO(watchLater, count);
    }

    @Override
    @Transactional
    public PlaylistDetailDTO toggleWatchLater(Long videoId, String userId) {
        PlaylistDTO watchLater = getOrCreateWatchLater(userId);
        Long playlistId = watchLater.getPlaylistId();

        boolean exists = playlistVideoRepository.existsByPlaylistIdAndVideo_VideoId(playlistId, videoId);
        if (exists) {
            return removeVideoFromPlaylist(playlistId, videoId, userId);
        } else {
            return addVideoToPlaylist(playlistId, videoId, userId);
        }
    }

    private PlaylistDTO toDTO(Playlist playlist, long videoCount) {
        return PlaylistDTO.builder()
                .playlistId(playlist.getPlaylistId())
                .title(playlist.getTitle())
                .description(playlist.getDescription())
                .userId(playlist.getUserId())
                .visibility(playlist.getVisibility())
                .isSystem(playlist.isSystem())
                .videoCount(videoCount)
                .createdAt(playlist.getCreatedAt())
                .updatedAt(playlist.getUpdatedAt())
                .build();
    }
}
