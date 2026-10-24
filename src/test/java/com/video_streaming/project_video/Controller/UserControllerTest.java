package com.video_streaming.project_video.Controller;

import com.video_streaming.project_video.DTOs.UserDTO;
import com.video_streaming.project_video.Service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testUpdateUser_JsonWithUserId() {
        UserDTO dto = UserDTO.builder()
                .userId("user-123")
                .fullName("John Doe")
                .username("johndoe")
                .avatar_url("https://avatar.png")
                .build();

        when(userService.updateUserProfile(any(UserDTO.class))).thenReturn("User updated successfully");

        ResponseEntity<String> response = userController.updateUserJson(dto, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("User updated successfully", response.getBody());
        verify(userService).updateUserProfile(argThat(u -> 
                "user-123".equals(u.getUserId()) && "John Doe".equals(u.getFullName())));
    }

    @Test
    void testUpdateUser_JsonWithoutUserId_ExtractsFromSecurityContext() {
        // User logged in as 'auth-user-99'
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("auth-user-99", null, Collections.emptyList())
        );

        UserDTO dto = UserDTO.builder()
                .fullName("Jane Doe")
                .username("janedoe")
                .build();

        when(userService.updateUserProfile(any(UserDTO.class))).thenReturn("User updated successfully");

        ResponseEntity<String> response = userController.updateUserJson(dto, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("User updated successfully", response.getBody());
        verify(userService).updateUserProfile(argThat(u -> "auth-user-99".equals(u.getUserId())));
    }

    @Test
    void testUpdateUser_FormData_Success() {
        // Simulates FormData sent by bideo_ui/utility/requests.ts
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("creator-42", null, Collections.emptyList())
        );

        UserDTO formDTO = new UserDTO();
        formDTO.setUserId("creator-42");
        formDTO.setUser_name("great_creator");
        formDTO.setUsername("great_creator");
        formDTO.setFullName("Great Creator");
        formDTO.setUser_email("creator@example.com");
        formDTO.setAvatar_url("https://example.com/avatar.jpg");

        when(userService.updateUserProfile(any(UserDTO.class))).thenReturn("User updated successfully");

        ResponseEntity<String> response = userController.updateUserFormData(formDTO, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("User updated successfully", response.getBody());
        verify(userService).updateUserProfile(argThat(u -> "great_creator".equals(u.getUsername())));
    }

    @Test
    void testUpdateUser_MissingUserId_ReturnsBadRequest() {
        // No security context, and no userId in body
        UserDTO dto = UserDTO.builder()
                .fullName("Anonymous")
                .username("anon")
                .build();

        ResponseEntity<String> response = userController.updateUserJson(dto, null, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("User ID is required"));
        verify(userService, never()).updateUserProfile(any());
    }

    @Test
    void testUpdateUser_ForbiddenWhenCallerTriesToUpdateDifferentUser() {
        // Logged in as 'user-A'
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-A", null, Collections.emptyList())
        );

        // Trying to update 'user-B'
        UserDTO dto = UserDTO.builder()
                .userId("user-B")
                .fullName("Hacked Name")
                .build();

        when(userService.getUserById("user-B")).thenReturn(UserDTO.builder().userId("user-B").build());

        ResponseEntity<String> response = userController.updateUserJson(dto, null, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(userService, never()).updateUserProfile(any());
    }

    @Test
    void testGetUserById() {
        UserDTO mockUser = UserDTO.builder()
                .userId("user-1")
                .username("mkbhd")
                .fullName("Marques Brownlee")
                .build();
        when(userService.getUserById("mkbhd")).thenReturn(mockUser);

        ResponseEntity<?> response = userController.getUserById("mkbhd");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockUser, response.getBody());
    }

    @Test
    void testSearchUsers() {
        List<UserDTO> results = List.of(UserDTO.builder().userId("1").username("tech_reviewer").build());
        when(userService.searchUsers("tech", 5)).thenReturn(results);

        ResponseEntity<List<UserDTO>> response = userController.searchUsers("tech", 5);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }
}
