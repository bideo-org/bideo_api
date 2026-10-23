package com.video_streaming.project_video.ServiceImplementation;

import com.video_streaming.project_video.DTOMapper.VideoDTOMapper;
import com.video_streaming.project_video.DTOs.SubscriptionStatusDTO;
import com.video_streaming.project_video.DTOs.VideoDTO;
import com.video_streaming.project_video.Entity.Subscription;
import com.video_streaming.project_video.Entity.Video;
import com.video_streaming.project_video.Repository.SubscriptionRepository;
import com.video_streaming.project_video.Repository.UserRepository;
import com.video_streaming.project_video.Repository.VideoRepository;
import com.video_streaming.project_video.Service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionServiceImpl.class);
    private static final String CHANNEL_SUB_COUNT_PREFIX = "channel:sub_count:";

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final VideoDTOMapper videoDTOMapper = new VideoDTOMapper();

    @Override
    @Transactional
    public SubscriptionStatusDTO toggleSubscribe(String subscriberUserId, String channelUserId) {
        if (subscriberUserId == null || channelUserId == null || subscriberUserId.isBlank() || channelUserId.isBlank()) {
            throw new IllegalArgumentException("Subscriber ID and Channel ID are required");
        }

        if (subscriberUserId.trim().equalsIgnoreCase(channelUserId.trim())) {
            throw new IllegalArgumentException("Users cannot subscribe to their own channel");
        }

        String subUid = subscriberUserId.trim();
        String chanUid = channelUserId.trim();

        userRepository.findById(chanUid)
                .orElseThrow(() -> new IllegalArgumentException("Channel user not found with ID: " + chanUid));

        Optional<Subscription> existingOpt = subscriptionRepository.findBySubscriberUserIdAndChannelUserId(subUid, chanUid);
        boolean isSubscribed;

        if (existingOpt.isPresent()) {
            // Unsubscribe
            subscriptionRepository.deleteBySubscriberUserIdAndChannelUserId(subUid, chanUid);
            isSubscribed = false;
            logger.info("User {} unsubscribed from channel {}", subUid, chanUid);
        } else {
            // Subscribe
            Subscription sub = Subscription.builder()
                    .subscriberUserId(subUid)
                    .channelUserId(chanUid)
                    .createdAt(new Date())
                    .build();
            subscriptionRepository.save(sub);
            isSubscribed = true;
            logger.info("User {} subscribed to channel {}", subUid, chanUid);
        }

        long count = subscriptionRepository.countByChannelUserId(chanUid);
        updateRedisCount(chanUid, count);

        return SubscriptionStatusDTO.builder()
                .channelUserId(chanUid)
                .subscriberCount(count)
                .isSubscribed(isSubscribed)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionStatusDTO getSubscriptionStatus(String subscriberUserId, String channelUserId) {
        if (channelUserId == null || channelUserId.isBlank()) {
            throw new IllegalArgumentException("Channel ID is required");
        }

        String chanUid = channelUserId.trim();
        long count = getSubscriberCount(chanUid);

        boolean isSubscribed = false;
        if (subscriberUserId != null && !subscriberUserId.isBlank()) {
            isSubscribed = subscriptionRepository.existsBySubscriberUserIdAndChannelUserId(
                    subscriberUserId.trim(), chanUid
            );
        }

        return SubscriptionStatusDTO.builder()
                .channelUserId(chanUid)
                .subscriberCount(count)
                .isSubscribed(isSubscribed)
                .build();
    }

    @Override
    public long getSubscriberCount(String channelUserId) {
        if (channelUserId == null || channelUserId.isBlank()) return 0L;

        String key = CHANNEL_SUB_COUNT_PREFIX + channelUserId.trim();
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return Long.parseLong(cached);
            } catch (NumberFormatException ignored) {
            }
        }

        long count = subscriptionRepository.countByChannelUserId(channelUserId.trim());
        updateRedisCount(channelUserId.trim(), count);
        return count;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VideoDTO> getSubscriptionFeed(String subscriberUserId, Pageable pageable) {
        if (subscriberUserId == null || subscriberUserId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        List<String> channelIds = subscriptionRepository.findSubscribedChannelIds(subscriberUserId.trim());
        if (channelIds == null || channelIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        Page<Video> feedPage = videoRepository.findSubscriptionFeed(channelIds, pageable);
        return feedPage.map(videoDTOMapper::convertEntityToDTO);
    }

    private void updateRedisCount(String channelUserId, long count) {
        try {
            String key = CHANNEL_SUB_COUNT_PREFIX + channelUserId;
            stringRedisTemplate.opsForValue().set(key, String.valueOf(count));
        } catch (Exception e) {
            logger.warn("Could not cache subscriber count in Redis: {}", e.getMessage());
        }
    }
}
