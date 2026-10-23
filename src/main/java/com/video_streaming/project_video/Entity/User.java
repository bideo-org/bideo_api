package com.video_streaming.project_video.Entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@Table(name = "user_info")
public class User {
    @Id
    private String userId;

    @Column(name = "user_name", nullable = false)
    private String user_name;

    @Column(name = "full_name")
    private String full_name;

    @Column(name = "user_email")
    private String user_email;

    @Column(name = "user_role")
    private String user_role;

    @Column(name = "avatar_url")
    private String avatar_url;

    // Backward-compatible 5-argument constructor
    public User(String userId, String user_name, String user_email, String user_role, String avatar_url) {
        this.userId = userId;
        this.user_name = user_name;
        this.full_name = user_name;
        this.user_email = user_email;
        this.user_role = user_role;
        this.avatar_url = avatar_url;
    }

    // Full 6-argument constructor
    public User(String userId, String user_name, String full_name, String user_email, String user_role, String avatar_url) {
        this.userId = userId;
        this.user_name = user_name;
        this.full_name = (full_name != null && !full_name.isBlank()) ? full_name : user_name;
        this.user_email = user_email;
        this.user_role = user_role;
        this.avatar_url = avatar_url;
    }

    public String getUsername() {
        return user_name;
    }

    public void setUsername(String username) {
        this.user_name = username;
    }

    public String getFullName() {
        return (full_name != null && !full_name.isBlank()) ? full_name : user_name;
    }

    public void setFullName(String fullName) {
        this.full_name = fullName;
    }
}