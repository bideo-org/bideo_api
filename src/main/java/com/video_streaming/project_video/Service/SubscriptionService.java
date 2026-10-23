package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.DTOs.SubscriptionStatusDTO;
import com.video_streaming.project_video.DTOs.VideoDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SubscriptionService {

    SubscriptionStatusDTO toggleSubscribe(String subscriberUserId, String channelUserId);

    SubscriptionStatusDTO getSubscriptionStatus(String subscriberUserId, String channelUserId);

    long getSubscriberCount(String channelUserId);

    Page<VideoDTO> getSubscriptionFeed(String subscriberUserId, Pageable pageable);
}
