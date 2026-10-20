package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.Repository.VideoRepository;
import com.video_streaming.project_video.ServiceImplementation.ViewTrackingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ViewTrackingServiceTest {

    private Map<String, Long> valueStore;
    private Set<String> setStore;
    private Map<Long, Long> dbIncrementedViews;
    private ViewTrackingServiceImpl viewTrackingService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        valueStore = new HashMap<>();
        setStore = new HashSet<>();
        dbIncrementedViews = new HashMap<>();

        ValueOperations<String, Long> valueOps = (ValueOperations<String, Long>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class<?>[]{ValueOperations.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("get".equals(name)) {
                        return valueStore.get((String) args[0]);
                    }
                    if ("setIfAbsent".equals(name)) {
                        String key = (String) args[0];
                        Long val = (Long) args[1];
                        if (valueStore.containsKey(key)) return false;
                        valueStore.put(key, val);
                        return true;
                    }
                    if ("increment".equals(name)) {
                        String key = (String) args[0];
                        Long cur = valueStore.getOrDefault(key, 0L);
                        valueStore.put(key, cur + 1L);
                        return cur + 1L;
                    }
                    if ("getAndDelete".equals(name)) {
                        return valueStore.remove((String) args[0]);
                    }
                    if ("set".equals(name)) {
                        valueStore.put((String) args[0], (Long) args[1]);
                        return null;
                    }
                    return null;
                }
        );

        SetOperations<String, String> setOps = (SetOperations<String, String>) Proxy.newProxyInstance(
                SetOperations.class.getClassLoader(),
                new Class<?>[]{SetOperations.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("add".equals(name)) {
                        String[] vals = (String[]) args[1];
                        setStore.addAll(Arrays.asList(vals));
                        return (long) vals.length;
                    }
                    if ("remove".equals(name)) {
                        Object[] vals = (Object[]) args[1];
                        for (Object v : vals) setStore.remove(v);
                        return (long) vals.length;
                    }
                    if ("members".equals(name)) {
                        return new HashSet<>(setStore);
                    }
                    return null;
                }
        );

        RedisTemplate<String, Long> redisTemplate = new RedisTemplate<>() {
            @Override
            public ValueOperations<String, Long> opsForValue() {
                return valueOps;
            }

            @Override
            public Boolean expire(String key, Duration timeout) {
                return true;
            }
        };

        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate() {
            @Override
            public SetOperations<String, String> opsForSet() {
                return setOps;
            }
        };

        VideoRepository videoRepository = (VideoRepository) Proxy.newProxyInstance(
                VideoRepository.class.getClassLoader(),
                new Class<?>[]{VideoRepository.class},
                (proxy, method, args) -> {
                    if ("incrementViews".equals(method.getName())) {
                        Long videoId = (Long) args[0];
                        Long views = (Long) args[1];
                        dbIncrementedViews.put(videoId, dbIncrementedViews.getOrDefault(videoId, 0L) + views);
                        return 1;
                    }
                    return null;
                }
        );

        viewTrackingService = new ViewTrackingServiceImpl(redisTemplate, stringRedisTemplate, videoRepository);
    }

    @Test
    @DisplayName("Should reject and ignore bot / crawler User-Agents")
    void testRecordView_withBotUserAgent() {
        boolean r1 = viewTrackingService.recordView(100L, "192.168.1.1", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", null);
        boolean r2 = viewTrackingService.recordView(100L, "192.168.1.2", "Twitterbot/1.0", null);
        boolean r3 = viewTrackingService.recordView(100L, "192.168.1.3", "facebookexternalhit/1.1", null);

        assertFalse(r1);
        assertFalse(r2);
        assertFalse(r3);
        assertEquals(0L, viewTrackingService.getPendingViews(100L));
    }

    @Test
    @DisplayName("Should successfully record first legitimate view and track in active video set")
    void testRecordView_legitimateFirstView() {
        boolean recorded = viewTrackingService.recordView(101L, "1.2.3.4", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)", null);

        assertTrue(recorded);
        assertEquals(1L, viewTrackingService.getPendingViews(101L));
        assertTrue(setStore.contains("101"));
    }

    @Test
    @DisplayName("Should deduplicate repeated views from same IP within cooldown window")
    void testRecordView_deduplicateRepeatedViews() {
        String ip = "10.0.0.1";
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

        boolean first = viewTrackingService.recordView(102L, ip, ua, null);
        boolean second = viewTrackingService.recordView(102L, ip, ua, null);
        boolean third = viewTrackingService.recordView(102L, ip, ua, null);

        assertTrue(first);
        assertFalse(second, "Second view within cooldown window should be deduplicated");
        assertFalse(third, "Third view within cooldown window should be deduplicated");
        assertEquals(1L, viewTrackingService.getPendingViews(102L), "Counter should only increment once");
    }

    @Test
    @DisplayName("Should record views separately for different authenticated user IDs")
    void testRecordView_differentUsers() {
        String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_5 like Mac OS X)";

        boolean u1 = viewTrackingService.recordView(103L, "1.1.1.1", ua, "user-alice");
        boolean u2 = viewTrackingService.recordView(103L, "1.1.1.1", ua, "user-bob");

        assertTrue(u1);
        assertTrue(u2);
        assertEquals(2L, viewTrackingService.getPendingViews(103L));
    }

    @Test
    @DisplayName("Should calculate effective views combining database and pending views")
    void testGetEffectiveViews() {
        viewTrackingService.recordView(104L, "1.1.1.1", "Mozilla/5.0", null);
        viewTrackingService.recordView(104L, "2.2.2.2", "Mozilla/5.0", null);

        Long effective = viewTrackingService.getEffectiveViews(104L, 500L);
        assertEquals(502L, effective);
    }

    @Test
    @DisplayName("Should flush pending views to database and clear pending set")
    void testFlushPendingViewsToDatabase() {
        viewTrackingService.recordView(105L, "1.1.1.1", "Mozilla/5.0", null);
        viewTrackingService.recordView(105L, "2.2.2.2", "Mozilla/5.0", null);
        viewTrackingService.recordView(106L, "3.3.3.3", "Mozilla/5.0", null);

        viewTrackingService.flushPendingViewsToDatabase();

        assertEquals(2L, dbIncrementedViews.get(105L));
        assertEquals(1L, dbIncrementedViews.get(106L));
        assertFalse(setStore.contains("105"));
        assertFalse(setStore.contains("106"));
        assertEquals(0L, viewTrackingService.getPendingViews(105L));
    }
}
