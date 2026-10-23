package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.DTOs.VideoEngagementDTO;
import com.video_streaming.project_video.Entity.Video;
import com.video_streaming.project_video.Entity.VideoLike;
import com.video_streaming.project_video.Repository.VideoLikeRepository;
import com.video_streaming.project_video.Repository.VideoRepository;
import com.video_streaming.project_video.ServiceImplementation.EngagementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EngagementServiceTest {

    private Map<String, Set<String>> redisStore;
    private Map<String, Map<String, String>> redisHashStore;
    private Map<Long, Video> videoDb;
    private Map<String, VideoLike> likeDb;

    private EngagementServiceImpl engagementService;
    private Video testVideo;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisStore = new HashMap<>();
        redisHashStore = new HashMap<>();
        videoDb = new HashMap<>();
        likeDb = new HashMap<>();

        testVideo = Video.builder()
                .videoId(10L)
                .video_title("Test Video")
                .likesCount(0L)
                .dislikesCount(0L)
                .build();
        videoDb.put(10L, testVideo);

        SetOperations<String, String> setOps = (SetOperations<String, String>) Proxy.newProxyInstance(
                SetOperations.class.getClassLoader(),
                new Class<?>[]{SetOperations.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    String key = (String) args[0];
                    if ("add".equals(name)) {
                        String[] values = (String[]) args[1];
                        redisStore.computeIfAbsent(key, k -> new HashSet<>()).addAll(Arrays.asList(values));
                        return (long) values.length;
                    }
                    if ("remove".equals(name)) {
                        Object[] values = (Object[]) args[1];
                        Set<String> set = redisStore.get(key);
                        if (set == null) return 0L;
                        long count = 0;
                        for (Object v : values) {
                            if (set.remove(v)) count++;
                        }
                        return count;
                    }
                    if ("isMember".equals(name)) {
                        Set<String> set = redisStore.get(key);
                        return set != null && set.contains(args[1]);
                    }
                    if ("size".equals(name)) {
                        Set<String> set = redisStore.get(key);
                        return set != null ? (long) set.size() : 0L;
                    }
                    if ("members".equals(name)) {
                        Set<String> set = redisStore.get(key);
                        return set != null ? new HashSet<>(set) : Collections.emptySet();
                    }
                    return null;
                }
        );

        HashOperations<String, Object, Object> hashOps = (HashOperations<String, Object, Object>) Proxy.newProxyInstance(
                HashOperations.class.getClassLoader(),
                new Class<?>[]{HashOperations.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    String key = (String) args[0];
                    if ("put".equals(name)) {
                        String hkey = (String) args[1];
                        String hval = (String) args[2];
                        redisHashStore.computeIfAbsent(key, k -> new HashMap<>()).put(hkey, hval);
                        return null;
                    }
                    if ("entries".equals(name)) {
                        Map<String, String> map = redisHashStore.get(key);
                        return map != null ? new HashMap<>(map) : Collections.emptyMap();
                    }
                    return null;
                }
        );

        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate() {
            @Override
            public SetOperations<String, String> opsForSet() {
                return setOps;
            }

            @Override
            public <HK, HV> HashOperations<String, HK, HV> opsForHash() {
                return (HashOperations<String, HK, HV>) hashOps;
            }

            @Override
            public Boolean hasKey(String key) {
                return redisStore.containsKey(key);
            }

            @Override
            public Boolean expire(String key, Duration timeout) {
                return true;
            }

            @Override
            public Boolean delete(String key) {
                redisHashStore.remove(key);
                redisStore.remove(key);
                return true;
            }
        };

        VideoRepository videoRepository = (VideoRepository) Proxy.newProxyInstance(
                VideoRepository.class.getClassLoader(),
                new Class<?>[]{VideoRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findById".equals(name)) {
                        Long id = (Long) args[0];
                        return Optional.ofNullable(videoDb.get(id));
                    }
                    if ("save".equals(name)) {
                        Video v = (Video) args[0];
                        videoDb.put(v.getVideoId(), v);
                        return v;
                    }
                    if ("updateEngagementCounts".equals(name)) {
                        Long id = (Long) args[0];
                        Long likes = (Long) args[1];
                        Long dislikes = (Long) args[2];
                        Video v = videoDb.get(id);
                        if (v != null) {
                            v.setLikesCount(likes);
                            v.setDislikesCount(dislikes);
                        }
                        return 1;
                    }
                    return null;
                }
        );

        VideoLikeRepository videoLikeRepository = (VideoLikeRepository) Proxy.newProxyInstance(
                VideoLikeRepository.class.getClassLoader(),
                new Class<?>[]{VideoLikeRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findByVideoIdAndUserId".equals(name)) {
                        Long vid = (Long) args[0];
                        String uid = (String) args[1];
                        return Optional.ofNullable(likeDb.get(vid + ":" + uid));
                    }
                    if ("findByVideoId".equals(name)) {
                        Long vid = (Long) args[0];
                        List<VideoLike> list = new ArrayList<>();
                        for (VideoLike vl : likeDb.values()) {
                            if (vl.getVideoId().equals(vid)) list.add(vl);
                        }
                        return list;
                    }
                    if ("save".equals(name)) {
                        VideoLike vl = (VideoLike) args[0];
                        likeDb.put(vl.getVideoId() + ":" + vl.getUserId(), vl);
                        return vl;
                    }
                    if ("deleteByVideoIdAndUserId".equals(name)) {
                        Long vid = (Long) args[0];
                        String uid = (String) args[1];
                        likeDb.remove(vid + ":" + uid);
                        return null;
                    }
                    return null;
                }
        );

        engagementService = new EngagementServiceImpl(videoRepository, videoLikeRepository, stringRedisTemplate);
    }

    @Test
    @DisplayName("Should instantly like an unrated video via Redis Set with zero DB blocking")
    void testToggleLike_newLike() {
        VideoEngagementDTO dto = engagementService.toggleLike(10L, "user-1");

        assertNotNull(dto);
        assertEquals("LIKE", dto.getUserReaction());
        assertEquals(1L, dto.getLikesCount());
        assertEquals(0L, dto.getDislikesCount());
        assertTrue(redisStore.get("video:likes:10").contains("user-1"));
        assertEquals("LIKE", redisHashStore.get("video:pending_user_likes:10").get("user-1"));
    }

    @Test
    @DisplayName("Should instantly toggle off like in Redis when clicked again")
    void testToggleLike_toggleOff() {
        engagementService.toggleLike(10L, "user-1"); // Like
        VideoEngagementDTO dto = engagementService.toggleLike(10L, "user-1"); // Toggle off

        assertEquals("NONE", dto.getUserReaction());
        assertEquals(0L, dto.getLikesCount());
        assertFalse(redisStore.get("video:likes:10").contains("user-1"));
        assertEquals("NONE", redisHashStore.get("video:pending_user_likes:10").get("user-1"));
    }

    @Test
    @DisplayName("Should switch reaction from dislike to like in Redis Sets")
    void testToggleLike_switchFromDislike() {
        engagementService.toggleDislike(10L, "user-1"); // Dislike
        assertTrue(redisStore.get("video:dislikes:10").contains("user-1"));

        VideoEngagementDTO dto = engagementService.toggleLike(10L, "user-1"); // Switch to like

        assertEquals("LIKE", dto.getUserReaction());
        assertEquals(1L, dto.getLikesCount());
        assertEquals(0L, dto.getDislikesCount());
        assertTrue(redisStore.get("video:likes:10").contains("user-1"));
        assertFalse(redisStore.get("video:dislikes:10").contains("user-1"));
        assertEquals("LIKE", redisHashStore.get("video:pending_user_likes:10").get("user-1"));
    }

    @Test
    @DisplayName("Should successfully dislike an unrated video via Redis Set")
    void testToggleDislike_newDislike() {
        VideoEngagementDTO dto = engagementService.toggleDislike(10L, "user-2");

        assertEquals("DISLIKE", dto.getUserReaction());
        assertEquals(0L, dto.getLikesCount());
        assertEquals(1L, dto.getDislikesCount());
        assertTrue(redisStore.get("video:dislikes:10").contains("user-2"));
        assertEquals("DISLIKE", redisHashStore.get("video:pending_user_likes:10").get("user-2"));
    }

    @Test
    @DisplayName("Should return engagement stats from Redis in sub-millisecond memory")
    void testGetEngagement() {
        engagementService.toggleLike(10L, "user-1");
        engagementService.toggleLike(10L, "user-2");
        engagementService.toggleDislike(10L, "user-3");

        // Anonymous user check
        VideoEngagementDTO anon = engagementService.getEngagement(10L, null);
        assertEquals(2L, anon.getLikesCount());
        assertEquals(1L, anon.getDislikesCount());
        assertEquals("NONE", anon.getUserReaction());

        // User-1 check (liked)
        VideoEngagementDTO user1 = engagementService.getEngagement(10L, "user-1");
        assertEquals("LIKE", user1.getUserReaction());

        // User-3 check (disliked)
        VideoEngagementDTO user3 = engagementService.getEngagement(10L, "user-3");
        assertEquals("DISLIKE", user3.getUserReaction());
    }

    @Test
    @DisplayName("Should flush pending Redis engagement counts and user reactions to PostgreSQL in background")
    void testFlushPendingEngagementToDatabase() {
        engagementService.toggleLike(10L, "user-1");
        engagementService.toggleLike(10L, "user-2");

        engagementService.flushPendingEngagementToDatabase();

        Video dbVideo = videoDb.get(10L);
        assertEquals(2L, dbVideo.getLikesCount());
        assertEquals(0L, dbVideo.getDislikesCount());
        assertNotNull(likeDb.get("10:user-1"));
        assertTrue(likeDb.get("10:user-1").isLike());
    }
}
