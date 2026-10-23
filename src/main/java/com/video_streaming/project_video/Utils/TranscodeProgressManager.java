package com.video_streaming.project_video.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Server-Sent Events (SSE) and live progress updates during multi-rendition ABR transcoding.
 */
@Component
public class TranscodeProgressManager {

    private static final Logger logger = LoggerFactory.getLogger(TranscodeProgressManager.class);

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<Long, TranscodeProgress> progressMap = new ConcurrentHashMap<>();

    public record TranscodeProgress(Long videoId, String stage, int percent, String status) {}

    public SseEmitter subscribe(Long videoId) {
        SseEmitter emitter = new SseEmitter(15 * 60 * 1000L); // 15 min timeout
        emitters.put(videoId, emitter);

        emitter.onCompletion(() -> emitters.remove(videoId));
        emitter.onTimeout(() -> emitters.remove(videoId));
        emitter.onError(e -> emitters.remove(videoId));

        TranscodeProgress current = progressMap.getOrDefault(
                videoId,
                new TranscodeProgress(videoId, "QUEUED", 5, "PROCESSING")
        );

        try {
            emitter.send(SseEmitter.event().name("progress").data(current));
        } catch (IOException e) {
            emitters.remove(videoId);
        }

        return emitter;
    }

    public void updateProgress(Long videoId, String stage, int percent, String status) {
        TranscodeProgress progress = new TranscodeProgress(videoId, stage, percent, status);
        progressMap.put(videoId, progress);
        logger.debug("Transcode progress for video {}: {} ({}%) - {}", videoId, stage, percent, status);

        SseEmitter emitter = emitters.get(videoId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("progress").data(progress));
                if ("COMPLETED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
                    emitter.complete();
                    emitters.remove(videoId);
                }
            } catch (Exception e) {
                emitters.remove(videoId);
            }
        }
    }

    public TranscodeProgress getProgress(Long videoId) {
        return progressMap.getOrDefault(videoId, new TranscodeProgress(videoId, "UNKNOWN", 0, "PENDING"));
    }
}
