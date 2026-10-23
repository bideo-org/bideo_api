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
        name = "watch_history",
        uniqueConstraints = @UniqueConstraint(name = "uk_watch_history_user_video", columnNames = {"user_id", "video_id"}),
        indexes = {
                @Index(name = "idx_watch_history_user_time", columnList = "user_id, last_watched_at")
        }
)
public class WatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Column(name = "progress_seconds", nullable = false)
    private Double progressSeconds;

    @Column(name = "duration_seconds")
    private Double durationSeconds;

    @Builder.Default
    @Column(name = "completed", nullable = false)
    private boolean completed = false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_watched_at", nullable = false)
    private Date lastWatchedAt;
}
