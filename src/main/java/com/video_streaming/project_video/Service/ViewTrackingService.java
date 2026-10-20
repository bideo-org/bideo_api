package com.video_streaming.project_video.Service;

public interface ViewTrackingService {

    /**
     * Records a video view with deduplication, cooldown window, and bot detection.
     *
     * @param videoId The ID of the video being viewed.
     * @param clientIp The client's IP address.
     * @param userAgent The client's User-Agent header string.
     * @param userId The optional authenticated user ID.
     * @return true if the view was legitimately counted, false if deduplicated or bot.
     */
    boolean recordView(Long videoId, String clientIp, String userAgent, String userId);

    /**
     * Gets the currently pending (unflushed) views for a video in Redis.
     *
     * @param videoId The ID of the video.
     * @return The count of pending views buffered in Redis.
     */
    Long getPendingViews(Long videoId);

    /**
     * Calculates the effective real-time view count (database views + pending Redis views).
     *
     * @param videoId The ID of the video.
     * @param databaseViews The persisted view count from PostgreSQL.
     * @return Total real-time view count.
     */
    Long getEffectiveViews(Long videoId, Long databaseViews);

    /**
     * Atomically drains pending view counts from Redis and increments them in PostgreSQL.
     */
    void flushPendingViewsToDatabase();
}
