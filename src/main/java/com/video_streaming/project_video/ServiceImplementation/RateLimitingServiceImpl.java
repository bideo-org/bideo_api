package com.video_streaming.project_video.ServiceImplementation;

import com.video_streaming.project_video.Service.RateLimitingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class RateLimitingServiceImpl implements RateLimitingService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingServiceImpl.class);
    private final StringRedisTemplate stringRedisTemplate;

    // In-memory fallback if Redis is down or unavailable
    private final Map<String, InMemoryCounter> inMemoryStore = new ConcurrentHashMap<>();

    private static class InMemoryCounter {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long resetTimeMs;

        InMemoryCounter(long resetTimeMs) {
            this.resetTimeMs = resetTimeMs;
        }
    }

    @Override
    public boolean isAllowed(String key, int maxRequests, int windowSeconds) {
        String redisKey = "ratelimit:" + key;
        try {
            Long current = stringRedisTemplate.opsForValue().increment(redisKey);
            if (current != null && current == 1L) {
                stringRedisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
            }
            if (current != null) {
                return current <= maxRequests;
            }
        } catch (Exception e) {
            logger.warn("Redis rate limiting unavailable, falling back to in-memory: {}", e.getMessage());
        }

        // In-memory fallback
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        InMemoryCounter counter = inMemoryStore.compute(key, (k, existing) -> {
            if (existing == null || now > existing.resetTimeMs) {
                InMemoryCounter fresh = new InMemoryCounter(now + windowMs);
                fresh.count.set(1);
                return fresh;
            } else {
                existing.count.incrementAndGet();
                return existing;
            }
        });

        return counter != null && counter.count.get() <= maxRequests;
    }

    @Override
    public void reset(String key) {
        try {
            stringRedisTemplate.delete("ratelimit:" + key);
        } catch (Exception ignored) {
        }
        inMemoryStore.remove(key);
    }
}
