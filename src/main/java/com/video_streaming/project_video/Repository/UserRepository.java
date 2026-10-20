package com.video_streaming.project_video.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.video_streaming.project_video.Entity.User;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    @Query("SELECT u FROM User u WHERE LOWER(u.user_name) = LOWER(:userName)")
    Optional<User> findFirstByUserNameIgnoreCase(@Param("userName") String userName);

    @Query("SELECT u FROM User u WHERE LOWER(u.user_email) = LOWER(:userEmail)")
    Optional<User> findByUserEmailIgnoreCase(@Param("userEmail") String userEmail);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.user_name) = LOWER(:userName)")
    boolean existsByUserNameIgnoreCase(@Param("userName") String userName);
}