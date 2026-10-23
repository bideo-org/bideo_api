package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.DTOs.CreatorAnalyticsDTO;
import com.video_streaming.project_video.Entity.User;
import com.video_streaming.project_video.Entity.Video;
import com.video_streaming.project_video.Repository.SubscriptionRepository;
import com.video_streaming.project_video.Repository.VideoRepository;
import com.video_streaming.project_video.ServiceImplementation.CreatorStudioServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CreatorStudioServiceTest {

    private List<Video> videoDb;
    private long subscriberCount = 0L;
    private CreatorStudioServiceImpl creatorStudioService;

    @BeforeEach
    void setUp() {
        videoDb = new ArrayList<>();
        User creator = new User("creator-42", "Alice Pro", "alice@example.com", "USER", "avatar.png");

        Video v1 = Video.builder()
                .videoId(101L)
                .video_title("Top 10 Spring Tips")
                .video_uploader(creator)
                .video_views(1000L)
                .likesCount(50L)
                .build();

        Video v2 = Video.builder()
                .videoId(102L)
                .video_title("Redis Caching Deep Dive")
                .video_uploader(creator)
                .video_views(500L)
                .likesCount(20L)
                .build();

        Video v3 = Video.builder()
                .videoId(103L)
                .video_title("Kubernetes Setup")
                .video_uploader(creator)
                .video_views(200L)
                .likesCount(10L)
                .build();

        videoDb.addAll(List.of(v1, v2, v3));
        subscriberCount = 42L;

        VideoRepository videoRepository = (VideoRepository) Proxy.newProxyInstance(
                VideoRepository.class.getClassLoader(),
                new Class<?>[]{VideoRepository.class},
                (proxy, method, args) -> {
                    if ("findByUploaderId".equals(method.getName())) {
                        Pageable pageable = (Pageable) args[1];
                        return new PageImpl<>(videoDb, pageable, videoDb.size());
                    }
                    return null;
                }
        );

        SubscriptionRepository subscriptionRepository = (SubscriptionRepository) Proxy.newProxyInstance(
                SubscriptionRepository.class.getClassLoader(),
                new Class<?>[]{SubscriptionRepository.class},
                (proxy, method, args) -> {
                    if ("countByChannelUserId".equals(method.getName())) {
                        return subscriberCount;
                    }
                    return 0L;
                }
        );

        creatorStudioService = new CreatorStudioServiceImpl(videoRepository, subscriptionRepository);
    }

    @Test
    @DisplayName("Should correctly calculate aggregated creator analytics and top performing videos")
    void testGetCreatorAnalytics() {
        CreatorAnalyticsDTO analytics = creatorStudioService.getCreatorAnalytics("creator-42");

        assertNotNull(analytics);
        assertEquals("creator-42", analytics.getCreatorUserId());
        assertEquals(3L, analytics.getTotalVideos());
        assertEquals(1700L, analytics.getTotalViews());
        assertEquals(80L, analytics.getTotalLikes());
        assertEquals(42L, analytics.getTotalSubscribers());
        assertEquals(3, analytics.getTopVideos().size());
        assertEquals("Top 10 Spring Tips", analytics.getTopVideos().get(0).getVideo_title());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when creatorUserId is null or blank")
    void testGetCreatorAnalytics_InvalidUserIdThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                creatorStudioService.getCreatorAnalytics(null));

        assertThrows(IllegalArgumentException.class, () ->
                creatorStudioService.getCreatorAnalytics("   "));
    }
}
