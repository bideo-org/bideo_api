package com.video_streaming.project_video.DTOMapper;

import com.video_streaming.project_video.DTOs.UserDTO;
import com.video_streaming.project_video.Entity.User;

public class UserDTOMapper {
        
    public UserDTO convertEntityToDTO(User user) {
        if (user == null) {
            return null;
        }
        UserDTO userDTO = new UserDTO();
        userDTO.setUserId(user.getUserId());
        userDTO.setUsername(user.getUsername());
        userDTO.setUser_name(user.getUser_name());
        userDTO.setFullName(user.getFullName());
        userDTO.setFull_name(user.getFullName());
        userDTO.setUser_email(user.getUser_email());
        userDTO.setAvatar_url(user.getAvatar_url());
        userDTO.setUser_role(user.getUser_role());

        return userDTO;
    }

    public User convertDTOToEntity(UserDTO userDTO) {
        if (userDTO == null) {
            return null;
        }
        User user = new User();
        user.setUserId(userDTO.getUserId());
        String handle = userDTO.getUsername() != null ? userDTO.getUsername() : userDTO.getUser_name();
        user.setUser_name(handle);
        String displayName = userDTO.getFullName() != null ? userDTO.getFullName() : userDTO.getFull_name();
        user.setFull_name(displayName != null ? displayName : handle);
        user.setUser_email(userDTO.getUser_email());
        user.setAvatar_url(userDTO.getAvatar_url());
        user.setUser_role(userDTO.getUser_role());

        return user;
    }

}