package com.video_streaming.project_video.ServiceImplementation;

import com.video_streaming.project_video.DTOs.VideoEngagementDTO;
import com.video_streaming.project_video.Entity.Video;
import com.video_streaming.project_video.Entity.VideoLike;
import com.video_streaming.project_video.Repository.VideoLikeRepository;
import com.video_streaming.project_video.Repository.VideoRepository;
import com.video_streaming.project_video.Service.EngagementService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EngagementServiceImpl implements EngagementService {

    private static final Logger logger = LoggerFactory.getLogger(EngagementServiceImpl.class);

    private static final String LIKES_KEY_PREFIX = "video:likes:";
    private static final String DISLIKES_KEY_PREFIX = "video:dislikes:";
    private static final String PENDING_USER_REACTIONS_PREFIX = "video:pending_user_likes:";
    private static final String PENDING_ENGAGEMENT_SET = "videos:pending_engagement_sync";
    private static final Duration ENGAGEMENT_TTL = Duration.ofDays(7);

    private final VideoRepository videoRepository;
    private final VideoLikeRepository videoLikeRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public VideoEngagementDTO toggleLike(Long videoId, String userId) {
        if (videoId == null || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Video ID and User ID are required");
        }

        String likesKey = LIKES_KEY_PREFIX + videoId;
        String dislikesKey = DISLIKES_KEY_PREFIX + videoId;
        String pendingUsersKey = PENDING_USER_REACTIONS_PREFIX + videoId;

        warmUpRedisIfNecessary(videoId);

        // Atomic in-memory state update
        Boolean isLiked = stringRedisTemplate.opsForSet().isMember(likesKey, userId);
        String finalReaction;

        if (Boolean.TRUE.equals(isLiked)) {
            // Toggle OFF like in RAM
            stringRedisTemplate.opsForSet().remove(likesKey, userId);
            stringRedisTemplate.opsForHash().put(pendingUsersKey, userId, "NONE");
            finalReaction = "NONE";
        } else {
            // Set LIKE in RAM (and remove dislike if present)
            stringRedisTemplate.opsForSet().add(likesKey, userId);
            stringRedisTemplate.opsForSet().remove(dislikesKey, userId);
            stringRedisTemplate.opsForHash().put(pendingUsersKey, userId, "LIKE");
            finalReaction = "LIKE";
        }

        // Renew TTL and mark video as dirty for write-behind background persistence
        stringRedisTemplate.expire(likesKey, ENGAGEMENT_TTL);
        stringRedisTemplate.expire(dislikesKey, ENGAGEMENT_TTL);
        stringRedisTemplate.expire(pendingUsersKey, ENGAGEMENT_TTL);
        stringRedisTemplate.opsForSet().add(PENDING_ENGAGEMENT_SET, String.valueOf(videoId));

        Long likesCount = getSetSize(likesKey);
        Long dislikesCount = getSetSize(dislikesKey);

        logger.debug("Fast toggleLike: videoId={}, userId={}, reaction={}, likes={}, dislikes={}",
                videoId, userId, finalReaction, likesCount, dislikesCount);

        return VideoEngagementDTO.builder()
                .videoId(videoId)
                .likesCount(likesCount)
                .dislikesCount(dislikesCount)
                .userReaction(finalReaction)
                .build();
    }

    @Override
    public VideoEngagementDTO toggleDislike(Long videoId, String userId) {
        if (videoId == null || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Video ID and User ID are required");
        }

        String likesKey = LIKES_KEY_PREFIX + videoId;
        String dislikesKey = DISLIKES_KEY_PREFIX + videoId;
        String pendingUsersKey = PENDING_USER_REACTIONS_PREFIX + videoId;

        warmUpRedisIfNecessary(videoId);

        Boolean isDisliked = stringRedisTemplate.opsForSet().isMember(dislikesKey, userId);
        String finalReaction;

        if (Boolean.TRUE.equals(isDisliked)) {
            // Toggle OFF dislike in RAM
            stringRedisTemplate.opsForSet().remove(dislikesKey, userId);
            stringRedisTemplate.opsForHash().put(pendingUsersKey, userId, "NONE");
            finalReaction = "NONE";
        } else {
            // Set DISLIKE in RAM (and remove like if present)
            stringRedisTemplate.opsForSet().add(dislikesKey, userId);
            stringRedisTemplate.opsForSet().remove(likesKey, userId);
            stringRedisTemplate.opsForHash().put(pendingUsersKey, userId, "DISLIKE");
            finalReaction = "DISLIKE";
        }

        stringRedisTemplate.expire(likesKey, ENGAGEMENT_TTL);
        stringRedisTemplate.expire(dislikesKey, ENGAGEMENT_TTL);
        stringRedisTemplate.expire(pendingUsersKey, ENGAGEMENT_TTL);
        stringRedisTemplate.opsForSet().add(PENDING_ENGAGEMENT_SET, String.valueOf(videoId));

        Long likesCount = getSetSize(likesKey);
        Long dislikesCount = getSetSize(dislikesKey);

        logger.debug("Fast toggleDislike: videoId={}, userId={}, reaction={}, likes={}, dislikes={}",
                videoId, userId, finalReaction, likesCount, dislikesCount);

        return VideoEngagementDTO.builder()
                .videoId(videoId)
                .likesCount(likesCount)
                .dislikesCount(dislikesCount)
                .userReaction(finalReaction)
                .build();
    }

    @Override
    public VideoEngagementDTO getEngagement(Long videoId, String userId) {
        if (videoId == null) {
            throw new IllegalArgumentException("Video ID is required");
        }

        String likesKey = LIKES_KEY_PREFIX + videoId;
        String dislikesKey = DISLIKES_KEY_PREFIX + videoId;

        warmUpRedisIfNecessary(videoId);

        Long likesCount = getSetSize(likesKey);
        Long dislikesCount = getSetSize(dislikesKey);

        String userReaction = "NONE";
        if (userId != null && !userId.isBlank()) {
            Boolean isLiked = stringRedisTemplate.opsForSet().isMember(likesKey, userId);
            if (Boolean.TRUE.equals(isLiked)) {
                userReaction = "LIKE";
            } else {
                Boolean isDisliked = stringRedisTemplate.opsForSet().isMember(dislikesKey, userId);
                if (Boolean.TRUE.equals(isDisliked)) {
                    userReaction = "DISLIKE";
                }
            }
        }

        return VideoEngagementDTO.builder()
                .videoId(videoId)
                .likesCount(likesCount)
                .dislikesCount(dislikesCount)
                .userReaction(userReaction)
                .build();
    }

    @Override
    public Long getLikesCount(Long videoId, Long fallbackDbCount) {
        if (videoId == null) return fallbackDbCount != null ? fallbackDbCount : 0L;
        String likesKey = LIKES_KEY_PREFIX + videoId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(likesKey))) {
            return getSetSize(likesKey);
        }
        return fallbackDbCount != null ? fallbackDbCount : 0L;
    }

    @Override
    public Long getDislikesCount(Long videoId, Long fallbackDbCount) {
        if (videoId == null) return fallbackDbCount != null ? fallbackDbCount : 0L;
        String dislikesKey = DISLIKES_KEY_PREFIX + videoId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(dislikesKey))) {
            return getSetSize(dislikesKey);
        }
        return fallbackDbCount != null ? fallbackDbCount : 0L;
    }

    @Override
    @Transactional
    public void flushPendingEngagementToDatabase() {
        Set<String> videoIdSet = stringRedisTemplate.opsForSet().members(PENDING_ENGAGEMENT_SET);
        if (videoIdSet == null || videoIdSet.isEmpty()) {
            return;
        }

        for (String videoIdStr : videoIdSet) {
            try {
                Long videoId = Long.parseLong(videoIdStr);
                String likesKey = LIKES_KEY_PREFIX + videoId;
                String dislikesKey = DISLIKES_KEY_PREFIX + videoId;
                String pendingUsersKey = PENDING_USER_REACTIONS_PREFIX + videoId;

                Long likesCount = getSetSize(likesKey);
                Long dislikesCount = getSetSize(dislikesKey);

                // 1. Update aggregated counts in PostgreSQL in a single fast query
                videoRepository.updateEngagementCounts(videoId, likesCount, dislikesCount);

                // 2. Persist individual pending user reactions to video_likes table
                Map<Object, Object> pendingEntries = stringRedisTemplate.opsForHash().entries(pendingUsersKey);
                if (!pendingEntries.isEmpty()) {
                    for (Map.Entry<Object, Object> entry : pendingEntries.entrySet()) {
                        String userId = (String) entry.getKey();
                        String action = (String) entry.getValue();

                        if ("NONE".equalsIgnoreCase(action)) {
                            videoLikeRepository.deleteByVideoIdAndUserId(videoId, userId);
                        } else if ("LIKE".equalsIgnoreCase(action) || "DISLIKE".equalsIgnoreCase(action)) {
                            boolean isLike = "LIKE".equalsIgnoreCase(action);
                            Optional<VideoLike> opt = videoLikeRepository.findByVideoIdAndUserId(videoId, userId);
                            if (opt.isPresent()) {
                                VideoLike vl = opt.get();
                                vl.setLike(isLike);
                                vl.setCreatedAt(new Date());
                                videoLikeRepository.save(vl);
                            } else {
                                VideoLike vl = VideoLike.builder()
                                        .videoId(videoId)
                                        .userId(userId)
                                        .isLike(isLike)
                                        .createdAt(new Date())
                                        .build();
                                videoLikeRepository.save(vl);
                            }
                        }
                    }
                    stringRedisTemplate.delete(pendingUsersKey);
                }

                stringRedisTemplate.opsForSet().remove(PENDING_ENGAGEMENT_SET, videoIdStr);
                logger.debug("Flushed engagement to DB for video {}: likes={}, dislikes={}, userUpdates={}",
                        videoId, likesCount, dislikesCount, pendingEntries.size());

            } catch (Exception e) {
                logger.error("Failed to flush engagement for video ID {}: {}", videoIdStr, e.getMessage());
            }
        }
    }

    private void warmUpRedisIfNecessary(Long videoId) {
        String likesKey = LIKES_KEY_PREFIX + videoId;
        String dislikesKey = DISLIKES_KEY_PREFIX + videoId;

        // If Redis sets don't exist yet, populate from DB once
        Boolean hasLikesKey = stringRedisTemplate.hasKey(likesKey);
        if (Boolean.FALSE.equals(hasLikesKey)) {
            // Verify video exists
            Video video = videoRepository.findById(videoId)
                    .orElseThrow(() -> new IllegalArgumentException("Video not found with ID: " + videoId));

            List<VideoLike> existingLikes = videoLikeRepository.findByVideoId(videoId);
            for (VideoLike vl : existingLikes) {
                if (vl.isLike()) {
                    stringRedisTemplate.opsForSet().add(likesKey, vl.getUserId());
                } else {
                    stringRedisTemplate.opsForSet().add(dislikesKey, vl.getUserId());
                }
            }
            stringRedisTemplate.expire(likesKey, ENGAGEMENT_TTL);
            stringRedisTemplate.expire(dislikesKey, ENGAGEMENT_TTL);
        }
    }

    private Long getSetSize(String key) {
        Long size = stringRedisTemplate.opsForSet().size(key);
        return size != null ? size : 0L;
    }
}
