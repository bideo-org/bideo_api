package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.DTOs.WatchProgressDTO;
import com.video_streaming.project_video.Entity.User;
import com.video_streaming.project_video.Entity.Video;
import com.video_streaming.project_video.Entity.WatchHistory;
import com.video_streaming.project_video.Repository.VideoRepository;
import com.video_streaming.project_video.Repository.WatchHistoryRepository;
import com.video_streaming.project_video.ServiceImplementation.WatchHistoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WatchHistoryServiceTest {

    private Map<Long, Video> videoStore;
    private Map<String, WatchHistory> historyStore;
    private WatchHistoryServiceImpl watchHistoryService;
    private Video testVideo;
    private long historyIdSeq = 1L;

    @BeforeEach
    void setUp() {
        videoStore = new HashMap<>();
        historyStore = new HashMap<>();

        User uploader = new User("uploader-1", "CodingChannel", "channel@test.com", "USER", "channel_avatar.png");
        testVideo = Video.builder()
                .videoId(200L)
                .video_title("Microservices Architecture")
                .slug("microservices-architecture-200")
                .thumbnail_url("https://s3.amazonaws.com/thumb.webp")
                .video_uploader(uploader)
                .build();
        videoStore.put(200L, testVideo);

        VideoRepository videoRepository = (VideoRepository) Proxy.newProxyInstance(
                VideoRepository.class.getClassLoader(),
                new Class<?>[]{VideoRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        return Optional.ofNullable(videoStore.get((Long) args[0]));
                    }
                    return null;
                }
        );

        WatchHistoryRepository watchHistoryRepository = (WatchHistoryRepository) Proxy.newProxyInstance(
                WatchHistoryRepository.class.getClassLoader(),
                new Class<?>[]{WatchHistoryRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findByUserIdAndVideo_VideoId".equals(name)) {
                        String uid = (String) args[0];
                        Long vid = (Long) args[1];
                        return Optional.ofNullable(historyStore.get(uid + ":" + vid));
                    }
                    if ("save".equals(name)) {
                        WatchHistory wh = (WatchHistory) args[0];
                        if (wh.getId() == null) wh.setId(historyIdSeq++);
                        historyStore.put(wh.getUserId() + ":" + wh.getVideo().getVideoId(), wh);
                        return wh;
                    }
                    if ("findByUserIdOrderByLastWatchedAtDesc".equals(name)) {
                        String uid = (String) args[0];
                        List<WatchHistory> list = new ArrayList<>();
                        for (WatchHistory wh : historyStore.values()) {
                            if (wh.getUserId().equals(uid)) list.add(wh);
                        }
                        list.sort((a, b) -> b.getLastWatchedAt().compareTo(a.getLastWatchedAt()));
                        return new PageImpl<>(list);
                    }
                    if ("deleteByUserIdAndVideoId".equals(name)) {
                        String uid = (String) args[0];
                        Long vid = (Long) args[1];
                        historyStore.remove(uid + ":" + vid);
                        return null;
                    }
                    if ("deleteAllByUserId".equals(name)) {
                        String uid = (String) args[0];
                        historyStore.values().removeIf(wh -> wh.getUserId().equals(uid));
                        return null;
                    }
                    return null;
                }
        );

        watchHistoryService = new WatchHistoryServiceImpl(watchHistoryRepository, videoRepository);
    }

    @Test
    @DisplayName("Should record initial playback progress and mark incomplete (<90%)")
    void testRecordProgress_incomplete() {
        WatchProgressDTO dto = watchHistoryService.recordProgress(200L, "user-1", 120.0, 300.0);

        assertNotNull(dto);
        assertEquals(200L, dto.getVideoId());
        assertEquals("Microservices Architecture", dto.getVideoTitle());
        assertEquals(120.0, dto.getProgressSeconds());
        assertEquals(300.0, dto.getDurationSeconds());
        assertFalse(dto.isCompleted(), "40% watched should not be marked completed");
        assertEquals("CodingChannel", dto.getChannelName());
    }

    @Test
    @DisplayName("Should mark video as completed when watched past 90% threshold")
    void testRecordProgress_completedThreshold() {
        // 275s out of 300s = 91.6%
        WatchProgressDTO dto = watchHistoryService.recordProgress(200L, "user-1", 275.0, 300.0);

        assertTrue(dto.isCompleted(), "91.6% watched should be marked completed");
    }

    @Test
    @DisplayName("Should update existing watch history record upon new progress heartbeat")
    void testRecordProgress_updateExisting() {
        watchHistoryService.recordProgress(200L, "user-1", 50.0, 300.0);
        WatchProgressDTO updated = watchHistoryService.recordProgress(200L, "user-1", 180.0, 300.0);

        assertEquals(180.0, updated.getProgressSeconds());
    }

    @Test
    @DisplayName("Should return saved progress for resume playback")
    void testGetProgress_found() {
        watchHistoryService.recordProgress(200L, "user-1", 85.5, 300.0);

        WatchProgressDTO result = watchHistoryService.getProgress(200L, "user-1");

        assertNotNull(result);
        assertEquals(85.5, result.getProgressSeconds());
    }

    @Test
    @DisplayName("Should return 0.0 seconds progress when not found in history")
    void testGetProgress_notFound() {
        WatchProgressDTO result = watchHistoryService.getProgress(200L, "user-1");

        assertNotNull(result);
        assertEquals(0.0, result.getProgressSeconds());
    }

    @Test
    @DisplayName("Should retrieve paginated user history")
    void testGetUserHistory() {
        watchHistoryService.recordProgress(200L, "user-1", 10.0, 100.0);

        Page<WatchProgressDTO> historyPage = watchHistoryService.getUserHistory("user-1", PageRequest.of(0, 10));

        assertEquals(1, historyPage.getTotalElements());
        assertEquals("Microservices Architecture", historyPage.getContent().get(0).getVideoTitle());
    }

    @Test
    @DisplayName("Should delete single history item and clear all history")
    void testDeleteAndClearHistory() {
        watchHistoryService.recordProgress(200L, "user-1", 10.0, 100.0);
        assertTrue(historyStore.containsKey("user-1:200"));

        watchHistoryService.deleteHistoryItem(200L, "user-1");
        assertFalse(historyStore.containsKey("user-1:200"));

        watchHistoryService.recordProgress(200L, "user-1", 20.0, 100.0);
        watchHistoryService.clearAllHistory("user-1");
        assertTrue(historyStore.isEmpty());
    }
}
