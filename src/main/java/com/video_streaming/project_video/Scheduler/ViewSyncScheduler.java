package com.video_streaming.project_video.Scheduler;

import com.video_streaming.project_video.Service.ViewTrackingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ViewSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ViewSyncScheduler.class);
    private final ViewTrackingService viewTrackingService;
    private final com.video_streaming.project_video.Service.EngagementService engagementService;

    // Run every 60 seconds to flush accumulated Redis view and engagement counts into PostgreSQL
    @Scheduled(fixedRateString = "${views.sync.rate.ms:60000}")
    public void syncViews() {
        try {
            viewTrackingService.flushPendingViewsToDatabase();
        } catch (Exception e) {
            logger.error("Error occurred while syncing pending views to database: {}", e.getMessage());
        }

        try {
            engagementService.flushPendingEngagementToDatabase();
        } catch (Exception e) {
            logger.error("Error occurred while syncing pending engagement to database: {}", e.getMessage());
        }
    }
}
