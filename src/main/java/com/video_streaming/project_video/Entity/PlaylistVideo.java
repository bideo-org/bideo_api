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
        name = "playlist_videos",
        uniqueConstraints = @UniqueConstraint(name = "uk_playlist_video", columnNames = {"playlist_id", "video_id"}),
        indexes = {
                @Index(name = "idx_pv_playlist", columnList = "playlist_id, position")
        }
)
public class PlaylistVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "playlist_id", nullable = false)
    private Long playlistId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Builder.Default
    @Column(name = "position", nullable = false)
    private int position = 0;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "added_at", nullable = false)
    private Date addedAt;
}
