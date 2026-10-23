package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.DTOs.VideoDTO;
import com.video_streaming.project_video.Entity.User;
import com.video_streaming.project_video.Entity.Video;
import com.video_streaming.project_video.Enums.VideoCategory;
import com.video_streaming.project_video.Enums.VideoStatus;
import com.video_streaming.project_video.Enums.VideoVisibility;
import com.video_streaming.project_video.Repository.VideoRepository;
import com.video_streaming.project_video.ServiceImplementation.DiscoveryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveryServiceTest {

    private Map<Long, Video> videoDb;
    private Map<String, String> redisDb;
    private DiscoveryServiceImpl discoveryService;

    private User creator1;
    private User creator2;
    private Video currentVideo;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        videoDb = new HashMap<>();
        redisDb = new HashMap<>();

        creator1 = new User("creator-1", "Lead Dev", "dev@buzzclips.app", "USER", "avatar1.png");
        creator2 = new User("creator-2", "Cloud Architect", "cloud@buzzclips.app", "USER", "avatar2.png");

        long now = System.currentTimeMillis();

        currentVideo = Video.builder()
                .videoId(1L)
                .video_title("Spring Boot & Redis Guide")
                .video_uploader(creator1)
                .category(VideoCategory.TECH)
                .visibility(VideoVisibility.PUBLIC)
                .videoStatus(VideoStatus.COMPLETED)
                .tags(List.of("spring", "redis"))
                .video_views(100L)
                .likesCount(10L)
                .video_uploadDate(new Date(now - 3600000L))
                .build();
        videoDb.put(1L, currentVideo);

        Video video2 = Video.builder()
                .videoId(2L)
                .video_title("Advanced Redis Caching")
                .video_uploader(creator2)
                .category(VideoCategory.TECH)
                .visibility(VideoVisibility.PUBLIC)
                .videoStatus(VideoStatus.COMPLETED)
                .tags(List.of("redis", "database"))
                .video_views(500L)
                .likesCount(40L)
                .video_uploadDate(new Date(now - 7200000L))
                .build();
        videoDb.put(2L, video2);

        Video video3 = Video.builder()
                .videoId(3L)
                .video_title("Docker for Beginners")
                .video_uploader(creator1)
                .category(VideoCategory.GAMING)
                .visibility(VideoVisibility.PUBLIC)
                .videoStatus(VideoStatus.COMPLETED)
                .tags(List.of("docker"))
                .video_views(200L)
                .likesCount(15L)
                .video_uploadDate(new Date(now - 14400000L))
                .build();
        videoDb.put(3L, video3);

        Video video4 = Video.builder()
                .videoId(4L)
                .video_title("Old Viral Video")
                .video_uploader(creator2)
                .category(VideoCategory.ENTERTAINMENT)
                .visibility(VideoVisibility.PUBLIC)
                .videoStatus(VideoStatus.COMPLETED)
                .tags(List.of("fun"))
                .video_views(1500L)
                .likesCount(20L)
                .video_uploadDate(new Date(now - 864000000L)) // 10 days ago
                .build();
        videoDb.put(4L, video4);

        VideoRepository videoRepository = (VideoRepository) Proxy.newProxyInstance(
                VideoRepository.class.getClassLoader(),
                new Class<?>[]{VideoRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findById".equals(name)) {
                        return Optional.ofNullable(videoDb.get((Long) args[0]));
                    }
                    if ("findRelatedByTags".equals(name)) {
                        Long curId = (Long) args[0];
                        List<String> tags = (List<String>) args[1];
                        List<Video> matches = videoDb.values().stream()
                                .filter(v -> !v.getVideoId().equals(curId))
                                .filter(v -> v.getTags().stream().anyMatch(t -> tags.contains(t.toLowerCase())))
                                .toList();
                        return new PageImpl<>(matches, (Pageable) args[2], matches.size());
                    }
                    if ("findRelatedByCategoryOrUploader".equals(name)) {
                        Long curId = (Long) args[0];
                        VideoCategory cat = (VideoCategory) args[1];
                        String uploaderId = (String) args[2];
                        List<Video> matches = videoDb.values().stream()
                                .filter(v -> !v.getVideoId().equals(curId))
                                .filter(v -> v.getCategory() == cat || (v.getVideo_uploader() != null && v.getVideo_uploader().getUserId().equals(uploaderId)))
                                .toList();
                        return new PageImpl<>(matches, (Pageable) args[3], matches.size());
                    }
                    if ("findByVisibilityAndVideoStatus".equals(name)) {
                        List<Video> matches = new ArrayList<>(videoDb.values());
                        return new PageImpl<>(matches, (Pageable) args[2], matches.size());
                    }
                    if ("findPopularVideos".equals(name)) {
                        List<Video> list = new ArrayList<>(videoDb.values());
                        return new PageImpl<>(list, (Pageable) args[0], list.size());
                    }
                    if ("findTitleSuggestions".equals(name)) {
                        String prefix = (String) args[0];
                        return videoDb.values().stream()
                                .map(Video::getVideo_title)
                                .filter(t -> t.toLowerCase().startsWith(prefix))
                                .toList();
                    }
                    if ("findTagSuggestions".equals(name)) {
                        String prefix = (String) args[0];
                        return videoDb.values().stream()
                                .flatMap(v -> v.getTags().stream())
                                .filter(t -> t.toLowerCase().startsWith(prefix))
                                .distinct()
                                .toList();
                    }
                    return null;
                }
        );

        ValueOperations<String, String> valueOps = (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class<?>[]{ValueOperations.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("get".equals(name)) {
                        return redisDb.get((String) args[0]);
                    }
                    if ("set".equals(name)) {
                        redisDb.put((String) args[0], (String) args[1]);
                        return null;
                    }
                    return null;
                }
        );

        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                return valueOps;
            }
        };

        discoveryService = new DiscoveryServiceImpl(videoRepository, stringRedisTemplate);
    }

    @Test
    @DisplayName("Should rank related videos by tag overlap and category match")
    void testGetRelatedVideos() {
        Page<VideoDTO> related = discoveryService.getRelatedVideos(1L, PageRequest.of(0, 10));

        assertNotNull(related);
        assertFalse(related.isEmpty());
        // Video 2 shares tag "redis" AND same TECH category, so it should rank highest
        assertEquals(2L, related.getContent().get(0).getVideoId());
        assertEquals("Advanced Redis Caching", related.getContent().get(0).getVideo_title());
    }

    @Test
    @DisplayName("Should rank high-velocity recent videos above old decayed viral videos in trending")
    void testGetTrendingVideos() {
        Page<VideoDTO> trending = discoveryService.getTrendingVideos(PageRequest.of(0, 10));

        assertNotNull(trending);
        assertFalse(trending.isEmpty());

        // Video 2 (uploaded 2h ago with 500 views & 40 likes) should outrank Video 4 (uploaded 10 days ago with 1500 views)
        long firstVideoId = trending.getContent().get(0).getVideoId();
        assertTrue(firstVideoId == 2L || firstVideoId == 1L);
        // Video 4 (10 days old) should be ranked last due to gravity decay
        long lastVideoId = trending.getContent().get(trending.getContent().size() - 1).getVideoId();
        assertEquals(4L, lastVideoId);
    }

    @Test
    @DisplayName("Should return autocomplete suggestions for search bar and cache in Redis")
    void testGetSearchSuggestions() {
        List<String> suggestions = discoveryService.getSearchSuggestions("spr", 5);

        assertNotNull(suggestions);
        assertFalse(suggestions.isEmpty());
        assertTrue(suggestions.contains("Spring Boot & Redis Guide"));
        assertTrue(suggestions.contains("spring"));

        // Verify Redis cache was written
        assertTrue(redisDb.containsKey("search:suggest:spr:5"));

        // Second call should return identical suggestions from cache
        List<String> cachedSuggestions = discoveryService.getSearchSuggestions("spr", 5);
        assertEquals(suggestions, cachedSuggestions);
    }

    @Test
    @DisplayName("Should return empty list for blank suggestion query")
    void testGetSearchSuggestions_BlankQuery() {
        List<String> empty = discoveryService.getSearchSuggestions("   ", 5);
        assertNotNull(empty);
        assertTrue(empty.isEmpty());
    }
}
