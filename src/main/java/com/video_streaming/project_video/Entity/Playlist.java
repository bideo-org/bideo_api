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
        name = "playlists",
        indexes = {
                @Index(name = "idx_playlist_user", columnList = "user_id")
        }
)
public class Playlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "playlist_id")
    private Long playlistId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Builder.Default
    @Column(name = "visibility")
    private String visibility = "PUBLIC"; // PUBLIC, PRIVATE, UNLISTED

    @Builder.Default
    @Column(name = "is_system")
    private boolean isSystem = false; // true for default "Watch Later"

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at")
    private Date updatedAt;
}
