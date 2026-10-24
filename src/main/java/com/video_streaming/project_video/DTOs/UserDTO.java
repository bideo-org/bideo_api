package com.video_streaming.project_video.DTOs;


import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("userId")
    @JsonAlias({"id", "uid", "user_id"})
    private String userId;

    @JsonProperty("username")
    @JsonAlias({"user_name", "userName", "handle"})
    private String username;

    @JsonProperty("user_name")
    public String getUser_name() {
        return username;
    }

    public void setUser_name(String user_name) {
        this.username = user_name;
    }

    @JsonProperty("fullName")
    @JsonAlias({"full_name", "displayName", "name"})
    private String fullName;

    @JsonProperty("full_name")
    public String getFull_name() {
        return fullName;
    }

    public void setFull_name(String full_name) {
        this.fullName = full_name;
    }

    @JsonProperty("user_email")
    @JsonAlias({"email", "userEmail"})
    private String user_email;

    public String getEmail() {
        return user_email;
    }

    public void setEmail(String email) {
        this.user_email = email;
    }

    @JsonProperty("avatar_url")
    @JsonAlias({"avatarUrl", "avatar"})
    private String avatar_url;

    public String getAvatarUrl() {
        return avatar_url;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatar_url = avatarUrl;
    }

    private String user_role;
}
