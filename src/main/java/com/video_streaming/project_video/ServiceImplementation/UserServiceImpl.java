package com.video_streaming.project_video.ServiceImplementation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.video_streaming.project_video.DTOMapper.UserDTOMapper;
import com.video_streaming.project_video.DTOs.UserDTO;
import com.video_streaming.project_video.Entity.User;
import com.video_streaming.project_video.Repository.UserRepository;
import com.video_streaming.project_video.Service.UserService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.UserRecord.CreateRequest;

import static com.video_streaming.project_video.Enums.SupportVariables.thumbnailURLDefault;

@Service
public class UserServiceImpl implements UserService {
    private static final String DUPLICATE_ACCOUNT_ERROR = "EMAIL_EXISTS";
    private static final String USER_ROLE_USER = "USER";

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,30}$");
    private static final Set<String> RESERVED_USERNAMES = Set.of(
            "admin", "administrator", "root", "system", "support", "help",
            "buzzclips", "bideo", "api", "auth", "login", "logout", "signup",
            "explore", "trending", "subscriptions", "playlists", "feed",
            "channel", "video", "videos", "embed", "settings", "profile",
            "null", "undefined", "anonymous", "moderator"
    );

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;
    private final com.video_streaming.project_video.Utils.UsernameBloomFilter usernameBloomFilter;

    // Backward-compatible 2-argument constructor for tests
    public UserServiceImpl(FirebaseAuth firebaseAuth, UserRepository userRepository) {
        this.firebaseAuth = firebaseAuth;
        this.userRepository = userRepository;
        this.usernameBloomFilter = new com.video_streaming.project_video.Utils.UsernameBloomFilter(null, userRepository);
    }

    // Full 3-argument constructor for Spring dependency injection
    @org.springframework.beans.factory.annotation.Autowired
    public UserServiceImpl(
            FirebaseAuth firebaseAuth,
            UserRepository userRepository,
            com.video_streaming.project_video.Utils.UsernameBloomFilter usernameBloomFilter
    ) {
        this.firebaseAuth = firebaseAuth;
        this.userRepository = userRepository;
        this.usernameBloomFilter = usernameBloomFilter != null
                ? usernameBloomFilter
                : new com.video_streaming.project_video.Utils.UsernameBloomFilter(null, userRepository);
    }

    public com.video_streaming.project_video.Utils.UsernameBloomFilter getUsernameBloomFilter() {
        return usernameBloomFilter;
    }

    public static String sanitizeUsername(String input) {
        if (input == null) return "";
        String clean = input.trim();
        if (clean.startsWith("@")) {
            clean = clean.substring(1).trim();
        }
        return clean;
    }

    public static void validateUsernameFormat(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (username.length() < 3 || username.length() > 30) {
            throw new IllegalArgumentException("Username must be between 3 and 30 characters in length");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("Username may only contain alphanumeric characters and underscores");
        }
        if (RESERVED_USERNAMES.contains(username.toLowerCase())) {
            throw new IllegalArgumentException("Username '" + username + "' is reserved and cannot be claimed");
        }
    }

    @Override
    @Transactional
    public void create(String emailId, String password, String user_name, String avatar_url) throws Exception {
        create(emailId, password, user_name, user_name, avatar_url);
    }

    @Override
    @Transactional
    public void create(String emailId, String password, String username, String fullName, String avatar_url) throws Exception {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        String cleanUsername = sanitizeUsername(username);
        validateUsernameFormat(cleanUsername);

        if (userRepository.existsByUserNameIgnoreCase(cleanUsername)) {
            throw new IllegalArgumentException("Username '" + cleanUsername + "' is already taken");
        }

        String cleanFullName = (fullName != null && !fullName.trim().isEmpty())
                ? fullName.trim()
                : cleanUsername;
        if (cleanFullName.length() > 100) {
            cleanFullName = cleanFullName.substring(0, 100).trim();
        }

        CreateRequest request = new CreateRequest();
        request.setEmail(emailId);
        request.setPassword(password);
        request.setDisplayName(cleanFullName);
        request.setEmailVerified(Boolean.TRUE);

        try {
            UserRecord userRecord = firebaseAuth.createUser(request);
            UserDTO userDTO = new UserDTO();
            userDTO.setUserId(userRecord.getUid());
            userDTO.setUsername(cleanUsername);
            userDTO.setUser_name(cleanUsername);
            userDTO.setFullName(cleanFullName);
            userDTO.setFull_name(cleanFullName);
            userDTO.setUser_email(userRecord.getEmail());
            userDTO.setAvatar_url(avatar_url != null && !avatar_url.isBlank() ? avatar_url : thumbnailURLDefault);
            updateUserMetadata(userDTO);
            usernameBloomFilter.add(cleanUsername);
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
        logger.info("User metadata stored for userId: {}", user.getUserId());
    }

    @Override
    @Transactional
    public String updateUserProfile(UserDTO userDTO) {
        if (userDTO == null || userDTO.getUserId() == null || userDTO.getUserId().isBlank()) {
            throw new IllegalArgumentException("User ID is required to update profile");
        }

        User user = userRepository.findById(userDTO.getUserId().trim())
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userDTO.getUserId()));

        // 1. Update Full Name / Channel Display Name
        String newFullName = userDTO.getFullName() != null ? userDTO.getFullName() : userDTO.getFull_name();
        if (newFullName != null && !newFullName.isBlank()) {
            String cleanName = newFullName.trim();
            if (cleanName.length() > 100) {
                cleanName = cleanName.substring(0, 100).trim();
            }
            user.setFull_name(cleanName);
        }

        // 2. Update Username / Handle (with uniqueness and format checks)
        String newUsername = userDTO.getUsername() != null ? userDTO.getUsername() : userDTO.getUser_name();
        if (newUsername != null && !newUsername.isBlank()) {
            String cleanHandle = sanitizeUsername(newUsername);
            if (!cleanHandle.equalsIgnoreCase(user.getUser_name())) {
                validateUsernameFormat(cleanHandle);
                if (userRepository.existsByUserNameIgnoreCaseAndUserIdNot(cleanHandle, user.getUserId())) {
                    throw new IllegalArgumentException("Username '" + cleanHandle + "' is already taken by another account");
                }
                user.setUser_name(cleanHandle);
                usernameBloomFilter.add(cleanHandle);
            }
        }

        // 3. Update Email
        if (userDTO.getUser_email() != null && !userDTO.getUser_email().isBlank()) {
            user.setUser_email(userDTO.getUser_email().trim());
        }

        // 4. Update Avatar URL
        if (userDTO.getAvatar_url() != null && !userDTO.getAvatar_url().isBlank()) {
            user.setAvatar_url(userDTO.getAvatar_url().trim());
        }

        userRepository.save(user);
        logger.info("User details updated successfully for userId: {}", user.getUserId());
        return "User updated successfully";
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getUserById(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new RuntimeException("User identifier is required");
        }
        String cleanIdentifier = sanitizeUsername(identifier);
        UserDTOMapper userDTOMapper = new UserDTOMapper();

        // 1. Try finding by primary key (userId)
        Optional<User> userOpt = userRepository.findById(identifier.trim());

        // 2. If not found by primary key, try finding by case-insensitive username handle
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findFirstByUserNameIgnoreCase(cleanIdentifier);
        }

        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found with ID or username: " + identifier);
        }

        return userDTOMapper.convertEntityToDTO(userOpt.get());
    }

    @Override
    public String resolveEmailFromIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return identifier;
        }
        String trimmed = identifier.trim();
        // If handle formatted with @ prefix (e.g. @johndoe)
        if (trimmed.startsWith("@")) {
            String clean = sanitizeUsername(trimmed);
            return userRepository.findFirstByUserNameIgnoreCase(clean)
                    .map(User::getUser_email)
                    .orElse(identifier);
        }
        // Standard email address contains @ inside
        if (trimmed.contains("@")) {
            return trimmed;
        }
        // Bare username
        return userRepository.findFirstByUserNameIgnoreCase(trimmed)
                .map(User::getUser_email)
                .orElse(trimmed);
    }

    @Override
    public boolean isUsernameValid(String username) {
        if (username == null || username.isBlank()) return false;
        String clean = sanitizeUsername(username);
        return USERNAME_PATTERN.matcher(clean).matches() && !RESERVED_USERNAMES.contains(clean.toLowerCase());
    }

    @Override
    public boolean isUsernameAvailable(String username) {
        if (username == null || username.isBlank()) return false;
        String clean = sanitizeUsername(username);
        if (!isUsernameValid(clean)) {
            return false;
        }

        // 1. Fast-path O(1) Bloom Filter check
        // If Bloom filter returns false, the username is 100% DEFINITELY NOT TAKEN.
        // No PostgreSQL query needed!
        if (usernameBloomFilter != null && !usernameBloomFilter.mightContain(clean)) {
            logger.debug("Bloom filter fast-path: username '{}' is available", clean);
            return true;
        }

        // 2. Probable match or false-positive (~1%): query database for ground truth
        return !userRepository.existsByUserNameIgnoreCase(clean);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> searchUsers(String query, int limit) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String cleanQuery = sanitizeUsername(query);
        int safeLimit = Math.max(1, Math.min(limit, 20));
        UserDTOMapper mapper = new UserDTOMapper();
        List<User> matches = userRepository.searchUsers(cleanQuery, PageRequest.of(0, safeLimit));
        return matches.stream().map(mapper::convertEntityToDTO).toList();
    }
}
