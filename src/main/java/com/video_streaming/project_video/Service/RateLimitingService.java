package com.video_streaming.project_video.Service;

public interface RateLimitingService {

    /**
     * Checks if the given key is allowed under the rate limit specification.
     *
     * @param key Unique key (e.g., ip:127.0.0.1:auth or user:uid:upload)
     * @param maxRequests Maximum allowed requests in the time window
     * @param windowSeconds Time window duration in seconds
     * @return true if request is within limit; false if limit exceeded
     */
    boolean isAllowed(String key, int maxRequests, int windowSeconds);

    /**
     * Resets rate limit counter for a specific key.
     */
    void reset(String key);
}
