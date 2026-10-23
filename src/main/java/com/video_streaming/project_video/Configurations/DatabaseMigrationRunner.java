package com.video_streaming.project_video.Configurations;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatabaseMigrationRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMigrationRunner.class);
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            logger.info("Checking and applying database schema migrations for video SEO and metadata...");

            // 0. Enable pg_trgm extension for fuzzy trigram search
            try {
                jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm;");
            } catch (Exception e) {
                logger.warn("Could not create pg_trgm extension: {}", e.getMessage());
            }

            // 1. Add new columns if missing
            jdbcTemplate.execute("ALTER TABLE vid_metadata ADD COLUMN IF NOT EXISTS slug VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE vid_metadata ADD COLUMN IF NOT EXISTS description TEXT;");
            jdbcTemplate.execute("ALTER TABLE vid_metadata ADD COLUMN IF NOT EXISTS category VARCHAR(50) DEFAULT 'OTHER';");
            jdbcTemplate.execute("ALTER TABLE vid_metadata ADD COLUMN IF NOT EXISTS visibility VARCHAR(50) DEFAULT 'PUBLIC';");
            jdbcTemplate.execute("ALTER TABLE vid_metadata ADD COLUMN IF NOT EXISTS iso_duration VARCHAR(50);");

            // 2. Backfill existing rows with defaults
            jdbcTemplate.execute("UPDATE vid_metadata SET visibility = 'PUBLIC' WHERE visibility IS NULL;");
            jdbcTemplate.execute("UPDATE vid_metadata SET category = 'OTHER' WHERE category IS NULL;");
            jdbcTemplate.execute("UPDATE vid_metadata SET slug = CONCAT('video-', video_id) WHERE slug IS NULL;");

            // 3. Create video_tags table if missing
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS video_tags (" +
                    "video_id BIGINT NOT NULL, " +
                    "tag VARCHAR(255) NOT NULL, " +
                    "CONSTRAINT fk_video_tags_video FOREIGN KEY (video_id) REFERENCES vid_metadata(video_id) ON DELETE CASCADE" +
                    ");");

            // 4. Create case-insensitive unique index on user_name for fast username lookup
            try {
                jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_user_info_user_name_lower ON user_info (LOWER(user_name));");
            } catch (Exception e) {
                logger.warn("Could not create unique index on user_info(user_name) (duplicates may exist): {}", e.getMessage());
            }

            // 5. Add likes_count and dislikes_count to vid_metadata
            jdbcTemplate.execute("ALTER TABLE vid_metadata ADD COLUMN IF NOT EXISTS likes_count BIGINT DEFAULT 0;");
            jdbcTemplate.execute("ALTER TABLE vid_metadata ADD COLUMN IF NOT EXISTS dislikes_count BIGINT DEFAULT 0;");
            jdbcTemplate.execute("UPDATE vid_metadata SET likes_count = 0 WHERE likes_count IS NULL;");
            jdbcTemplate.execute("UPDATE vid_metadata SET dislikes_count = 0 WHERE dislikes_count IS NULL;");

            // 6. Create video_likes table
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS video_likes (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "video_id BIGINT NOT NULL, " +
                    "user_id VARCHAR(255) NOT NULL, " +
                    "is_like BOOLEAN NOT NULL, " +
                    "created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL, " +
                    "CONSTRAINT uk_video_likes_video_user UNIQUE (video_id, user_id)" +
                    ");");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_video_likes_video ON video_likes(video_id);");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_video_likes_user ON video_likes(user_id);");

            // 7. Create video_comments table
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS video_comments (" +
                    "comment_id BIGSERIAL PRIMARY KEY, " +
                    "video_id BIGINT NOT NULL, " +
                    "user_id VARCHAR(255) NOT NULL, " +
                    "parent_comment_id BIGINT, " +
                    "content TEXT NOT NULL, " +
                    "timestamp_in_seconds INTEGER, " +
                    "like_count BIGINT DEFAULT 0, " +
                    "is_pinned BOOLEAN DEFAULT FALSE, " +
                    "created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL, " +
                    "updated_at TIMESTAMP WITHOUT TIME ZONE, " +
                    "CONSTRAINT fk_comments_video FOREIGN KEY (video_id) REFERENCES vid_metadata(video_id) ON DELETE CASCADE, " +
                    "CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES user_info(\"userId\") ON DELETE CASCADE" +
                    ");");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_comments_video_created ON video_comments(video_id, created_at);");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_comments_parent ON video_comments(parent_comment_id);");

            // 8. Create watch_history table
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS watch_history (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "user_id VARCHAR(255) NOT NULL, " +
                    "video_id BIGINT NOT NULL, " +
                    "progress_seconds DOUBLE PRECISION NOT NULL, " +
                    "duration_seconds DOUBLE PRECISION, " +
                    "completed BOOLEAN DEFAULT FALSE, " +
                    "last_watched_at TIMESTAMP WITHOUT TIME ZONE NOT NULL, " +
                    "CONSTRAINT uk_watch_history_user_video UNIQUE (user_id, video_id), " +
                    "CONSTRAINT fk_watch_history_video FOREIGN KEY (video_id) REFERENCES vid_metadata(video_id) ON DELETE CASCADE" +
                    ");");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_watch_history_user_time ON watch_history(user_id, last_watched_at);");

            logger.info("Database schema migration completed successfully.");
        } catch (Exception e) {
            logger.warn("Schema migration notice (columns may already exist or running in restricted mode): {}", e.getMessage());
        }
    }
}
