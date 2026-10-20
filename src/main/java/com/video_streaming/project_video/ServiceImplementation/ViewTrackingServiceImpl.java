package com.video_streaming.project_video.ServiceImplementation;

import com.video_streaming.project_video.Repository.VideoRepository;
import com.video_streaming.project_video.Service.ViewTrackingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ViewTrackingServiceImpl implements ViewTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(ViewTrackingServiceImpl.class);

    private static final String PENDING_VIEWS_KEY_PREFIX = "video:pendingViews:";
    private static final String PENDING_VIDEOS_SET_KEY = "videos:pending_views_set";
    private static final String DEDUP_KEY_PREFIX = "view:dedup:";

    // Bot / Crawler detection regex
    private static final Pattern BOT_PATTERN = Pattern.compile(
            "(?i)(bot|crawler|spider|slurp|facebookexternalhit|whatsapp|twitterbot|headless|lighthouse|preview|google-read-aloud|googlebot|bingbot|yandexbot)"
    );

    @Value("${views.cooldown.minutes:30}")
    private int cooldownMinutes = 30;

    private final RedisTemplate<String, Long> viewsRedisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final VideoRepository videoRepository;

    @Override
    public boolean recordView(Long videoId, String clientIp, String userAgent, String userId) {
        if (videoId == null) {
            return false;
        }

        // 1. Filter out automated bots, scrapers, and link prefetchers
        if (isBot(userAgent)) {
            logger.debug("Ignored view for video {} from bot User-Agent: {}", videoId, userAgent);
            return false;
        }

        // 2. Identify the viewer fingerprint (UserId preferred, client IP fallback)
        String fingerprint = buildFingerprint(clientIp, userId);
        String dedupKey = DEDUP_KEY_PREFIX + videoId + ":" + fingerprint;

        // 3. Atomically check-and-set cooldown in Redis
        Boolean isNewView = viewsRedisTemplate.opsForValue().setIfAbsent(
                dedupKey,
                1L,
                Duration.ofMinutes(Math.max(cooldownMinutes, 1))
        );

        if (!Boolean.TRUE.equals(isNewView)) {
            logger.debug("Deduplicated repeated view for video {} from {}", videoId, fingerprint);
            return false;
        }

        // 4. Increment the pending views counter and track videoId in active set
        String pendingKey = PENDING_VIEWS_KEY_PREFIX + videoId;
        viewsRedisTemplate.opsForValue().increment(pendingKey);
        viewsRedisTemplate.expire(pendingKey, Duration.ofDays(7));
        stringRedisTemplate.opsForSet().add(PENDING_VIDEOS_SET_KEY, String.valueOf(videoId));

        logger.debug("Legitimate view recorded for video {} by {}", videoId, fingerprint);
        return true;
    }

    @Override
    public Long getPendingViews(Long videoId) {
        if (videoId == null) {
            return 0L;
        }
        try {
            Long pending = viewsRedisTemplate.opsForValue().get(PENDING_VIEWS_KEY_PREFIX + videoId);
            return pending != null ? pending : 0L;
        } catch (Exception e) {
            logger.warn("Failed to get pending views for video {}: {}", videoId, e.getMessage());
            return 0L;
        }
    }

    @Override
    public Long getEffectiveViews(Long videoId, Long databaseViews) {
        long db = databaseViews != null ? databaseViews : 0L;
        return db + getPendingViews(videoId);
    }

    @Override
    @Transactional
    public void flushPendingViewsToDatabase() {
        Set<String> videoIdSet;
        try {
            videoIdSet = stringRedisTemplate.opsForSet().members(PENDING_VIDEOS_SET_KEY);
        } catch (Exception e) {
            logger.error("Failed to fetch pending video set from Redis: {}", e.getMessage());
            return;
        }

        if (videoIdSet == null || videoIdSet.isEmpty()) {
            return;
        }

        for (String videoIdStr : videoIdSet) {
            try {
                Long videoId = Long.parseLong(videoIdStr);
                String pendingKey = PENDING_VIEWS_KEY_PREFIX + videoId;

                Long pending = null;
                try {
                    // Atomic get-and-delete to prevent lost updates from concurrent increments
                    pending = viewsRedisTemplate.opsForValue().getAndDelete(pendingKey);
                } catch (Exception ex) {
                    // Safe fallback for Redis instances prior to 6.2
                    pending = viewsRedisTemplate.opsForValue().get(pendingKey);
                    if (pending != null && pending > 0) {
                        viewsRedisTemplate.delete(pendingKey);
                    }
                }

                if (pending != null && pending > 0) {
                    videoRepository.incrementViews(videoId, pending);
                    logger.info("Flushed {} pending views for video ID: {}", pending, videoId);
                }

                // Remove video ID from pending set
                stringRedisTemplate.opsForSet().remove(PENDING_VIDEOS_SET_KEY, videoIdStr);
            } catch (Exception e) {
                logger.error("Failed to flush views for video ID {}: {}", videoIdStr, e.getMessage());
            }
        }
    }

    private boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        return BOT_PATTERN.matcher(userAgent).find();
    }

    private String buildFingerprint(String clientIp, String userId) {
        if (userId != null && !userId.isBlank()) {
            return "uid:" + userId.trim();
        }
        if (clientIp != null && !clientIp.isBlank()) {
            return "ip:" + clientIp.trim();
        }
        return "anon:unknown";
    }
}
