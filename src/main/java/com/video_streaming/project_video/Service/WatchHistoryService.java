package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.DTOs.WatchProgressDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WatchHistoryService {

    WatchProgressDTO recordProgress(Long videoId, String userId, Double progressSeconds, Double durationSeconds);

    WatchProgressDTO getProgress(Long videoId, String userId);

    Page<WatchProgressDTO> getUserHistory(String userId, Pageable pageable);

    void deleteHistoryItem(Long videoId, String userId);

    void clearAllHistory(String userId);
}
