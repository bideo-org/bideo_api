package com.video_streaming.project_video.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubscriptionStatusDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String channelUserId;
    private long subscriberCount;
    private boolean isSubscribed;
}
