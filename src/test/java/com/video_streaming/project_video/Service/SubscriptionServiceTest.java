package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.DTOs.SubscriptionStatusDTO;
import com.video_streaming.project_video.DTOs.VideoDTO;
import com.video_streaming.project_video.Entity.Subscription;
import com.video_streaming.project_video.Entity.User;
import com.video_streaming.project_video.Entity.Video;
import com.video_streaming.project_video.Repository.SubscriptionRepository;
import com.video_streaming.project_video.Repository.UserRepository;
import com.video_streaming.project_video.Repository.VideoRepository;
import com.video_streaming.project_video.ServiceImplementation.SubscriptionServiceImpl;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionServiceTest {

    private Map<String, User> userDb;
    private List<Subscription> subscriptionDb;
    private List<Video> videoDb;
    private Map<String, String> redisValueDb;

    private SubscriptionServiceImpl subscriptionService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userDb = new HashMap<>();
        subscriptionDb = new ArrayList<>();
        videoDb = new ArrayList<>();
        redisValueDb = new HashMap<>();

        User creator = new User("creator-1", "Tech Lead", "lead@example.com", "USER", "avatar.png");
        User subscriber = new User("sub-1", "Subscriber User", "sub@example.com", "USER", "avatar2.png");
        userDb.put("creator-1", creator);
        userDb.put("sub-1", subscriber);

        Video v1 = Video.builder()
                .videoId(100L)
                .video_title("Clean Architecture Guide")
                .video_uploader(creator)
                .build();
        videoDb.add(v1);

        SubscriptionRepository subscriptionRepository = (SubscriptionRepository) Proxy.newProxyInstance(
                SubscriptionRepository.class.getClassLoader(),
                new Class<?>[]{SubscriptionRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findBySubscriberUserIdAndChannelUserId".equals(name)) {
                        String subUid = (String) args[0];
                        String chanUid = (String) args[1];
                        return subscriptionDb.stream()
                                .filter(s -> s.getSubscriberUserId().equals(subUid) && s.getChannelUserId().equals(chanUid))
                                .findFirst();
                    }
                    if ("deleteBySubscriberUserIdAndChannelUserId".equals(name)) {
                        String subUid = (String) args[0];
                        String chanUid = (String) args[1];
                        subscriptionDb.removeIf(s -> s.getSubscriberUserId().equals(subUid) && s.getChannelUserId().equals(chanUid));
                        return null;
                    }
                    if ("save".equals(name)) {
                        Subscription sub = (Subscription) args[0];
                        subscriptionDb.add(sub);
                        return sub;
                    }
                    if ("countByChannelUserId".equals(name)) {
                        String chanUid = (String) args[0];
                        return (long) subscriptionDb.stream().filter(s -> s.getChannelUserId().equals(chanUid)).count();
                    }
                    if ("existsBySubscriberUserIdAndChannelUserId".equals(name)) {
                        String subUid = (String) args[0];
                        String chanUid = (String) args[1];
                        return subscriptionDb.stream().anyMatch(s -> s.getSubscriberUserId().equals(subUid) && s.getChannelUserId().equals(chanUid));
                    }
                    if ("findSubscribedChannelIds".equals(name)) {
                        String subUid = (String) args[0];
                        return subscriptionDb.stream()
                                .filter(s -> s.getSubscriberUserId().equals(subUid))
                                .map(Subscription::getChannelUserId)
                                .toList();
                    }
                    return null;
                }
        );

        UserRepository userRepository = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        return Optional.ofNullable(userDb.get((String) args[0]));
                    }
                    return null;
                }
        );

        VideoRepository videoRepository = (VideoRepository) Proxy.newProxyInstance(
                VideoRepository.class.getClassLoader(),
                new Class<?>[]{VideoRepository.class},
                (proxy, method, args) -> {
                    if ("findSubscriptionFeed".equals(method.getName())) {
                        List<String> channelIds = (List<String>) args[0];
                        Pageable pageable = (Pageable) args[1];
                        List<Video> matching = videoDb.stream()
                                .filter(v -> v.getVideo_uploader() != null && channelIds.contains(v.getVideo_uploader().getUserId()))
                                .toList();
                        return new PageImpl<>(matching, pageable, matching.size());
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
                        return redisValueDb.get((String) args[0]);
                    }
                    if ("set".equals(name)) {
                        redisValueDb.put((String) args[0], (String) args[1]);
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

        subscriptionService = new SubscriptionServiceImpl(
                subscriptionRepository,
                userRepository,
                videoRepository,
                stringRedisTemplate
        );
    }

    @Test
    @DisplayName("Should successfully subscribe to a channel and update subscriber count")
    void testToggleSubscribe_Subscribe() {
        SubscriptionStatusDTO result = subscriptionService.toggleSubscribe("sub-1", "creator-1");

        assertTrue(result.isSubscribed());
        assertEquals("creator-1", result.getChannelUserId());
        assertEquals(1L, result.getSubscriberCount());
        assertEquals(1, subscriptionDb.size());
        assertEquals("1", redisValueDb.get("channel:sub_count:creator-1"));
    }

    @Test
    @DisplayName("Should unsubscribe when user is already subscribed")
    void testToggleSubscribe_Unsubscribe() {
        // Pre-subscribe
        subscriptionDb.add(Subscription.builder()
                .subscriberUserId("sub-1")
                .channelUserId("creator-1")
                .build());

        SubscriptionStatusDTO result = subscriptionService.toggleSubscribe("sub-1", "creator-1");

        assertFalse(result.isSubscribed());
        assertEquals(0L, result.getSubscriberCount());
        assertEquals(0, subscriptionDb.size());
        assertEquals("0", redisValueDb.get("channel:sub_count:creator-1"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when user attempts to subscribe to self")
    void testToggleSubscribe_SelfSubscriptionThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                subscriptionService.toggleSubscribe("creator-1", "creator-1"));

        assertTrue(ex.getMessage().contains("Users cannot subscribe to their own channel"));
    }

    @Test
    @DisplayName("Should return accurate subscription status for logged-in and anonymous users")
    void testGetSubscriptionStatus() {
        subscriptionDb.add(Subscription.builder()
                .subscriberUserId("sub-1")
                .channelUserId("creator-1")
                .build());

        SubscriptionStatusDTO subStatus = subscriptionService.getSubscriptionStatus("sub-1", "creator-1");
        assertTrue(subStatus.isSubscribed());
        assertEquals(1L, subStatus.getSubscriberCount());

        SubscriptionStatusDTO anonStatus = subscriptionService.getSubscriptionStatus(null, "creator-1");
        assertFalse(anonStatus.isSubscribed());
        assertEquals(1L, anonStatus.getSubscriberCount());
    }

    @Test
    @DisplayName("Should retrieve subscription feed of videos from subscribed channels")
    void testGetSubscriptionFeed() {
        subscriptionDb.add(Subscription.builder()
                .subscriberUserId("sub-1")
                .channelUserId("creator-1")
                .build());

        Page<VideoDTO> feed = subscriptionService.getSubscriptionFeed("sub-1", PageRequest.of(0, 10));

        assertNotNull(feed);
        assertEquals(1, feed.getTotalElements());
        assertEquals("Clean Architecture Guide", feed.getContent().get(0).getVideo_title());
    }

    @Test
    @DisplayName("Should return empty feed when user has zero subscriptions")
    void testGetSubscriptionFeed_NoSubscriptions() {
        Page<VideoDTO> feed = subscriptionService.getSubscriptionFeed("sub-1", PageRequest.of(0, 10));

        assertNotNull(feed);
        assertEquals(0, feed.getTotalElements());
    }
}
