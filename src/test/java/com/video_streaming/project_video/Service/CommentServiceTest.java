package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.DTOs.CommentDTO;
import com.video_streaming.project_video.DTOs.CommentRequestDTO;
import com.video_streaming.project_video.Entity.Comment;
import com.video_streaming.project_video.Entity.User;
import com.video_streaming.project_video.Entity.Video;
import com.video_streaming.project_video.Repository.CommentRepository;
import com.video_streaming.project_video.Repository.UserRepository;
import com.video_streaming.project_video.Repository.VideoRepository;
import com.video_streaming.project_video.ServiceImplementation.CommentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CommentServiceTest {

    private Map<Long, Comment> commentStore;
    private Map<Long, Video> videoStore;
    private Map<String, User> userStore;
    private CommentServiceImpl commentService;

    private Video testVideo;
    private User videoCreator;
    private User commentAuthor;
    private long commentIdSequence = 1L;

    @BeforeEach
    void setUp() {
        commentStore = new HashMap<>();
        videoStore = new HashMap<>();
        userStore = new HashMap<>();

        videoCreator = new User("creator-1", "Creator", "creator@example.com", "USER", "avatar1.png");
        commentAuthor = new User("author-1", "Author", "author@example.com", "USER", "avatar2.png");

        testVideo = Video.builder()
                .videoId(100L)
                .video_title("Spring Boot Guide")
                .video_uploader(videoCreator)
                .build();

        videoStore.put(100L, testVideo);
        userStore.put("creator-1", videoCreator);
        userStore.put("author-1", commentAuthor);

        VideoRepository videoRepository = (VideoRepository) Proxy.newProxyInstance(
                VideoRepository.class.getClassLoader(),
                new Class<?>[]{VideoRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        return Optional.ofNullable(videoStore.get((Long) args[0]));
                    }
                    return null;
                }
        );

        UserRepository userRepository = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        return Optional.ofNullable(userStore.get((String) args[0]));
                    }
                    return null;
                }
        );

        CommentRepository commentRepository = (CommentRepository) Proxy.newProxyInstance(
                CommentRepository.class.getClassLoader(),
                new Class<?>[]{CommentRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findById".equals(name)) {
                        return Optional.ofNullable(commentStore.get((Long) args[0]));
                    }
                    if ("save".equals(name)) {
                        Comment c = (Comment) args[0];
                        if (c.getCommentId() == null) {
                            c.setCommentId(commentIdSequence++);
                        }
                        commentStore.put(c.getCommentId(), c);
                        return c;
                    }
                    if ("delete".equals(name)) {
                        Comment c = (Comment) args[0];
                        commentStore.remove(c.getCommentId());
                        return null;
                    }
                    if ("deleteByParentCommentId".equals(name)) {
                        Long parentId = (Long) args[0];
                        commentStore.values().removeIf(c -> parentId.equals(c.getParentCommentId()));
                        return null;
                    }
                    if ("countByParentCommentId".equals(name)) {
                        Long parentId = (Long) args[0];
                        return commentStore.values().stream()
                                .filter(c -> parentId.equals(c.getParentCommentId()))
                                .count();
                    }
                    return null;
                }
        );

        commentService = new CommentServiceImpl(commentRepository, videoRepository, userRepository);
    }

    @Test
    @DisplayName("Should add top-level comment and auto-extract timestamp from text")
    void testAddComment_withTimestampExtraction() {
        CommentRequestDTO request = CommentRequestDTO.builder()
                .content("Loved the demo at 02:15, great pacing!")
                .build();

        CommentDTO result = commentService.addComment(100L, "author-1", request);

        assertNotNull(result);
        assertNotNull(result.getCommentId());
        assertEquals("Loved the demo at 02:15, great pacing!", result.getContent());
        assertEquals(135, result.getTimestampInSeconds(), "02:15 should be parsed to 135 seconds");
        assertNull(result.getParentCommentId());
    }

    @Test
    @DisplayName("Should add reply to parent comment and maintain 2-tier shallow hierarchy")
    void testAddComment_nestedReply() {
        Comment parent = Comment.builder()
                .commentId(50L)
                .video(testVideo)
                .user(commentAuthor)
                .content("Root comment")
                .build();
        commentStore.put(50L, parent);

        CommentRequestDTO replyRequest = CommentRequestDTO.builder()
                .content("I agree with you!")
                .parentCommentId(50L)
                .build();

        CommentDTO reply = commentService.addComment(100L, "creator-1", replyRequest);

        assertNotNull(reply);
        assertEquals(50L, reply.getParentCommentId());
    }

    @Test
    @DisplayName("Should allow author to edit comment")
    void testUpdateComment_authorSuccess() {
        Comment comment = Comment.builder()
                .commentId(20L)
                .video(testVideo)
                .user(commentAuthor)
                .content("Initial content")
                .build();
        commentStore.put(20L, comment);

        CommentDTO updated = commentService.updateComment(20L, "author-1", "Updated content at 01:00");

        assertEquals("Updated content at 01:00", updated.getContent());
        assertEquals(60, updated.getTimestampInSeconds());
    }

    @Test
    @DisplayName("Should reject comment update from non-author")
    void testUpdateComment_unauthorized() {
        Comment comment = Comment.builder()
                .commentId(20L)
                .video(testVideo)
                .user(commentAuthor)
                .content("Initial content")
                .build();
        commentStore.put(20L, comment);

        assertThrows(AccessDeniedException.class, () ->
                commentService.updateComment(20L, "stranger-99", "Hacked content"));
    }

    @Test
    @DisplayName("Should allow video creator to delete any comment on their video")
    void testDeleteComment_byVideoCreator() {
        Comment comment = Comment.builder()
                .commentId(30L)
                .video(testVideo)
                .user(commentAuthor)
                .build();
        commentStore.put(30L, comment);

        // Video creator deletes comment
        commentService.deleteComment(30L, "creator-1");

        assertFalse(commentStore.containsKey(30L));
    }

    @Test
    @DisplayName("Should allow video creator to pin comment")
    void testTogglePinComment_byVideoCreator() {
        Comment comment = Comment.builder()
                .commentId(40L)
                .video(testVideo)
                .user(commentAuthor)
                .isPinned(false)
                .build();
        commentStore.put(40L, comment);

        CommentDTO pinned = commentService.togglePinComment(40L, "creator-1");
        assertTrue(pinned.isPinned());
    }
}
