package com.video_streaming.project_video.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uk_subscription_subscriber_channel", columnNames = {"subscriber_user_id", "channel_user_id"}),
        indexes = {
                @Index(name = "idx_sub_channel", columnList = "channel_user_id"),
                @Index(name = "idx_sub_subscriber", columnList = "subscriber_user_id")
        }
)
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscriber_user_id", nullable = false)
    private String subscriberUserId;

    @Column(name = "channel_user_id", nullable = false)
    private String channelUserId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt;
}
