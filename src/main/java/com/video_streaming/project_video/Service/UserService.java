package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.DTOs.UserDTO;

public interface UserService {
    
    /**
     * This method is used to create a new user with the provided email, password, and username.
     * 
     * @param emailId The email ID of the user.
     * @param password The password for the user.
     * @param user_name The username for the user.
     * @param avatar_url The avatar url for the user.
     * @throws Exception If there is an error during user creation.
     */
    void create(String emailId, String password, String user_name, String avatar_url) throws Exception;

    /**
     * This method is used to update the user profile with the provided UserDTO.
     * 
     * @param userDTO The UserDTO containing user details to be updated.
     * @return A string indicating the result of the update operation.
     */
    String updateUserProfile(UserDTO userDTO);

    /**
     * This method retrieves a user by their user ID.
     * 
     * @param userId The ID of the user to retrieve.
     * @return UserDTO containing user details.
     */
    UserDTO getUserById(String userId);

    /**
     * Resolves the user's email from their identifier (which may be a username or an email).
     *
     * @param identifier The username or email entered by the user.
     * @return The resolved email address.
     */
    String resolveEmailFromIdentifier(String identifier);

    /**
     * Checks if a given username is available (not already taken).
     *
     * @param username The username to check.
     * @return true if available, false if already taken.
     */
    boolean isUsernameAvailable(String username);
}
