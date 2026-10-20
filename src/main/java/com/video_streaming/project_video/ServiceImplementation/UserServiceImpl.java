package com.video_streaming.project_video.ServiceImplementation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.video_streaming.project_video.DTOMapper.UserDTOMapper;
import com.video_streaming.project_video.DTOs.UserDTO;
import com.video_streaming.project_video.Entity.User;
import com.video_streaming.project_video.Repository.UserRepository;
import com.video_streaming.project_video.Service.UserService;

import java.util.Optional;
import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Transactional;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.UserRecord.CreateRequest;

import static com.video_streaming.project_video.Enums.SupportVariables.thumbnailURLDefault;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private static final String DUPLICATE_ACCOUNT_ERROR = "EMAIL_EXISTS";
    private static final String USER_ROLE_USER = "USER";

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;

    @Transactional
    public void create(String emailId, String password, String user_name, String avatar_url) throws Exception {
        if (user_name == null || user_name.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        String cleanUsername = user_name.trim();
        if (userRepository.existsByUserNameIgnoreCase(cleanUsername)) {
            throw new IllegalArgumentException("Username '" + cleanUsername + "' is already taken");
        }

        CreateRequest request = new CreateRequest();
        request.setEmail(emailId);
        request.setPassword(password);
        request.setDisplayName(cleanUsername);
        request.setEmailVerified(Boolean.TRUE);

        try {
            UserRecord userRecord = firebaseAuth.createUser(request);
            UserDTO userDTO = new UserDTO();
            userDTO.setUserId(userRecord.getUid());
            userDTO.setUser_name(userRecord.getDisplayName());
            userDTO.setUser_email(userRecord.getEmail());
            userDTO.setAvatar_url(avatar_url!=null?avatar_url:thumbnailURLDefault);
            updateUserMetadata(userDTO);
        } catch (FirebaseAuthException exception) {
            if (exception.getMessage().contains(DUPLICATE_ACCOUNT_ERROR)) {
                throw new Exception("Account with given email-id already exists");
            }
            throw exception;
        }
    }

    @Transactional
    protected void updateUserMetadata(UserDTO userDTO) {
        UserDTOMapper userDTOMapper = new UserDTOMapper();
        User user = userDTOMapper.convertDTOToEntity(userDTO);
        user.setUser_role(USER_ROLE_USER);
        userRepository.save(user);
        logger.info("User details stored");
    }

    @Transactional
    public String updateUserProfile(UserDTO userDTO) {
        User user = userRepository.getReferenceById(userDTO.getUserId());

            if (user == null) {
                throw new RuntimeException("User not found with ID: " + userDTO.getUserId());
            }
            if (userDTO.getUser_name() != null) {
                user.setUser_name(userDTO.getUser_name());
            }
            if (userDTO.getUser_email() != null) {
                user.setUser_email(userDTO.getUser_email());
            }
            if (userDTO.getAvatar_url() != null) {
                user.setAvatar_url(userDTO.getAvatar_url());
            }
            userRepository.save(user);
            logger.info("User details updated");
            return "User updated successfully";
    }

    public UserDTO getUserById(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new RuntimeException("User identifier is required");
        }
        String trimmed = identifier.trim();
        UserDTOMapper userDTOMapper = new UserDTOMapper();

        // 1. Try finding by primary key (userId)
        Optional<User> userOpt = userRepository.findById(trimmed);

        // 2. If not found by UUID/userId, try finding by case-insensitive userName
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findFirstByUserNameIgnoreCase(trimmed);
        }

        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found with ID or username: " + identifier);
        }

        return userDTOMapper.convertEntityToDTO(userOpt.get());
    }

    public String resolveEmailFromIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return identifier;
        }
        String trimmed = identifier.trim();
        if (trimmed.contains("@")) {
            return trimmed;
        }
        return userRepository.findFirstByUserNameIgnoreCase(trimmed)
                .map(User::getUser_email)
                .orElse(trimmed);
    }

    public boolean isUsernameAvailable(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return !userRepository.existsByUserNameIgnoreCase(username.trim());
    }
}
