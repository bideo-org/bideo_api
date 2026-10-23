package com.video_streaming.project_video.Repository;

import com.video_streaming.project_video.Entity.Subscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findBySubscriberUserIdAndChannelUserId(String subscriberUserId, String channelUserId);

    boolean existsBySubscriberUserIdAndChannelUserId(String subscriberUserId, String channelUserId);

    long countByChannelUserId(String channelUserId);

    long countBySubscriberUserId(String subscriberUserId);

    @Query("SELECT s.channelUserId FROM Subscription s WHERE s.subscriberUserId = :subscriberUserId")
    List<String> findSubscribedChannelIds(@Param("subscriberUserId") String subscriberUserId);

    @Modifying
    @Query("DELETE FROM Subscription s WHERE s.subscriberUserId = :subscriberUserId AND s.channelUserId = :channelUserId")
    void deleteBySubscriberUserIdAndChannelUserId(
            @Param("subscriberUserId") String subscriberUserId,
            @Param("channelUserId") String channelUserId
    );
}
