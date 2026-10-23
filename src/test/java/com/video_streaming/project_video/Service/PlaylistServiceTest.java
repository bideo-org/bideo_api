package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.DTOs.PlaylistDTO;
import com.video_streaming.project_video.DTOs.PlaylistDetailDTO;
import com.video_streaming.project_video.Entity.Playlist;
import com.video_streaming.project_video.Entity.PlaylistVideo;
import com.video_streaming.project_video.Entity.User;
import com.video_streaming.project_video.Entity.Video;
import com.video_streaming.project_video.Repository.PlaylistRepository;
import com.video_streaming.project_video.Repository.PlaylistVideoRepository;
import com.video_streaming.project_video.Repository.VideoRepository;
import com.video_streaming.project_video.ServiceImplementation.PlaylistServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PlaylistServiceTest {

    private Map<Long, Playlist> playlistDb;
    private List<PlaylistVideo> playlistVideoDb;
    private Map<Long, Video> videoDb;
    private long playlistIdSeq = 1L;
    private long playlistVideoIdSeq = 1L;

    private PlaylistServiceImpl playlistService;
    private User testUser;
    private Video testVideo;

    @BeforeEach
    void setUp() {
        playlistDb = new HashMap<>();
        playlistVideoDb = new ArrayList<>();
        videoDb = new HashMap<>();

        testUser = new User("user-1", "Playlist Owner", "owner@example.com", "USER", "avatar.png");
        testVideo = Video.builder()
                .videoId(500L)
                .video_title("Spring Boot & Redis High Performance")
                .video_uploader(testUser)
                .build();
        videoDb.put(500L, testVideo);

        PlaylistRepository playlistRepository = (PlaylistRepository) Proxy.newProxyInstance(
                PlaylistRepository.class.getClassLoader(),
                new Class<?>[]{PlaylistRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("save".equals(name)) {
                        Playlist p = (Playlist) args[0];
                        if (p.getPlaylistId() == null) {
                            p.setPlaylistId(playlistIdSeq++);
                        }
                        playlistDb.put(p.getPlaylistId(), p);
                        return p;
                    }
                    if ("findById".equals(name)) {
                        return Optional.ofNullable(playlistDb.get((Long) args[0]));
                    }
                    if ("findByUserIdOrderByCreatedAtDesc".equals(name)) {
                        String uid = (String) args[0];
                        return playlistDb.values().stream()
                                .filter(p -> p.getUserId().equals(uid))
                                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                                .toList();
                    }
                    if ("findByUserIdAndIsSystem".equals(name)) {
                        String uid = (String) args[0];
                        boolean isSystem = (Boolean) args[1];
                        return playlistDb.values().stream()
                                .filter(p -> p.getUserId().equals(uid) && p.isSystem() == isSystem)
                                .findFirst();
                    }
                    if ("delete".equals(name)) {
                        Playlist p = (Playlist) args[0];
                        playlistDb.remove(p.getPlaylistId());
                        return null;
                    }
                    return null;
                }
        );

        PlaylistVideoRepository playlistVideoRepository = (PlaylistVideoRepository) Proxy.newProxyInstance(
                PlaylistVideoRepository.class.getClassLoader(),
                new Class<?>[]{PlaylistVideoRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("save".equals(name)) {
                        PlaylistVideo pv = (PlaylistVideo) args[0];
                        if (pv.getId() == null) {
                            pv.setId(playlistVideoIdSeq++);
                        }
                        playlistVideoDb.add(pv);
                        return pv;
                    }
                    if ("countByPlaylistId".equals(name)) {
                        Long pId = (Long) args[0];
                        return (long) playlistVideoDb.stream().filter(pv -> pv.getPlaylistId().equals(pId)).count();
                    }
                    if ("findByPlaylistIdOrderByPositionAsc".equals(name)) {
                        Long pId = (Long) args[0];
                        return playlistVideoDb.stream()
                                .filter(pv -> pv.getPlaylistId().equals(pId))
                                .sorted(Comparator.comparingInt(PlaylistVideo::getPosition))
                                .toList();
                    }
                    if ("existsByPlaylistIdAndVideo_VideoId".equals(name)) {
                        Long pId = (Long) args[0];
                        Long vId = (Long) args[1];
                        return playlistVideoDb.stream().anyMatch(pv ->
                                pv.getPlaylistId().equals(pId) && pv.getVideo() != null && pv.getVideo().getVideoId().equals(vId));
                    }
                    if ("deleteByPlaylistIdAndVideoId".equals(name)) {
                        Long pId = (Long) args[0];
                        Long vId = (Long) args[1];
                        playlistVideoDb.removeIf(pv ->
                                pv.getPlaylistId().equals(pId) && pv.getVideo() != null && pv.getVideo().getVideoId().equals(vId));
                        return null;
                    }
                    if ("deleteByPlaylistId".equals(name)) {
                        Long pId = (Long) args[0];
                        playlistVideoDb.removeIf(pv -> pv.getPlaylistId().equals(pId));
                        return null;
                    }
                    return null;
                }
        );

        VideoRepository videoRepository = (VideoRepository) Proxy.newProxyInstance(
                VideoRepository.class.getClassLoader(),
                new Class<?>[]{VideoRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        return Optional.ofNullable(videoDb.get((Long) args[0]));
                    }
                    return null;
                }
        );

        playlistService = new PlaylistServiceImpl(playlistRepository, playlistVideoRepository, videoRepository);
    }

    @Test
    @DisplayName("Should create custom playlist successfully")
    void testCreatePlaylist() {
        PlaylistDTO playlist = playlistService.createPlaylist("user-1", "My Favorites", "Cool videos", "PUBLIC");

        assertNotNull(playlist.getPlaylistId());
        assertEquals("My Favorites", playlist.getTitle());
        assertEquals("PUBLIC", playlist.getVisibility());
        assertFalse(playlist.isSystem());
        assertEquals(0, playlist.getVideoCount());
    }

    @Test
    @DisplayName("Should auto-provision Watch Later system playlist when querying user playlists")
    void testGetUserPlaylists_AutoProvisionsWatchLater() {
        List<PlaylistDTO> playlists = playlistService.getUserPlaylists("user-1");

        assertFalse(playlists.isEmpty());
        assertTrue(playlists.stream().anyMatch(p -> "Watch Later".equals(p.getTitle()) && p.isSystem()));
    }

    @Test
    @DisplayName("Should add and remove video from playlist")
    void testAddAndRemoveVideo() {
        PlaylistDTO created = playlistService.createPlaylist("user-1", "Learn Spring", null, "PUBLIC");
        Long playlistId = created.getPlaylistId();

        // Add
        PlaylistDetailDTO withVideo = playlistService.addVideoToPlaylist(playlistId, 500L, "user-1");
        assertEquals(1, withVideo.getVideoCount());
        assertEquals("Spring Boot & Redis High Performance", withVideo.getVideos().get(0).getVideo_title());

        // Remove
        PlaylistDetailDTO removed = playlistService.removeVideoFromPlaylist(playlistId, 500L, "user-1");
        assertEquals(0, removed.getVideoCount());
    }

    @Test
    @DisplayName("Should block unauthorized non-owner from modifying playlist")
    void testUnauthorizedModificationBlocked() {
        PlaylistDTO created = playlistService.createPlaylist("user-1", "Private Collection", null, "PUBLIC");
        Long playlistId = created.getPlaylistId();

        assertThrows(AccessDeniedException.class, () ->
                playlistService.addVideoToPlaylist(playlistId, 500L, "intruder-user"));

        assertThrows(AccessDeniedException.class, () ->
                playlistService.removeVideoFromPlaylist(playlistId, 500L, "intruder-user"));

        assertThrows(AccessDeniedException.class, () ->
                playlistService.deletePlaylist(playlistId, "intruder-user"));
    }

    @Test
    @DisplayName("Should enforce privacy on private playlists")
    void testPrivatePlaylistAccessDeniedForOtherUsers() {
        PlaylistDTO created = playlistService.createPlaylist("user-1", "Secret Notes", null, "PRIVATE");
        Long playlistId = created.getPlaylistId();

        // Owner can access
        PlaylistDetailDTO ownerView = playlistService.getPlaylistDetails(playlistId, "user-1");
        assertNotNull(ownerView);

        // Anonymous blocked
        assertThrows(AccessDeniedException.class, () ->
                playlistService.getPlaylistDetails(playlistId, null));

        // Different user blocked
        assertThrows(AccessDeniedException.class, () ->
                playlistService.getPlaylistDetails(playlistId, "intruder-user"));
    }

    @Test
    @DisplayName("Should prevent deletion of Watch Later system playlist")
    void testPreventDeletingSystemPlaylist() {
        PlaylistDTO watchLater = playlistService.getOrCreateWatchLater("user-1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                playlistService.deletePlaylist(watchLater.getPlaylistId(), "user-1"));

        assertTrue(ex.getMessage().contains("System default playlists cannot be deleted"));
    }

    @Test
    @DisplayName("Should toggle Watch Later membership on and off")
    void testToggleWatchLater() {
        // First call adds
        PlaylistDetailDTO added = playlistService.toggleWatchLater(500L, "user-1");
        assertEquals(1, added.getVideoCount());

        // Second call removes
        PlaylistDetailDTO removed = playlistService.toggleWatchLater(500L, "user-1");
        assertEquals(0, removed.getVideoCount());
    }
}
