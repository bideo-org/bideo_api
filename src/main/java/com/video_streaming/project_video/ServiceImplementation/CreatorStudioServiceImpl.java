package com.video_streaming.project_video.ServiceImplementation;

import com.video_streaming.project_video.DTOMapper.VideoDTOMapper;
import com.video_streaming.project_video.DTOs.CreatorAnalyticsDTO;
import com.video_streaming.project_video.DTOs.VideoDTO;
import com.video_streaming.project_video.Entity.Video;
import com.video_streaming.project_video.Repository.SubscriptionRepository;
import com.video_streaming.project_video.Repository.VideoRepository;
import com.video_streaming.project_video.Service.CreatorStudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CreatorStudioServiceImpl implements CreatorStudioService {

    private final VideoRepository videoRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final VideoDTOMapper videoDTOMapper = new VideoDTOMapper();

    @Override
    @Transactional(readOnly = true)
    public CreatorAnalyticsDTO getCreatorAnalytics(String creatorUserId) {
        if (creatorUserId == null || creatorUserId.isBlank()) {
            throw new IllegalArgumentException("Creator User ID is required");
        }

        String uid = creatorUserId.trim();

        // 1. Fetch creator's videos ordered by views
        Page<Video> videoPage = videoRepository.findByUploaderId(
                uid,
                PageRequest.of(0, 100, Sort.by("video_views").descending())
        );

        List<Video> videos = videoPage.getContent();
        long totalVideos = videoPage.getTotalElements();

        long totalViews = 0L;
        long totalLikes = 0L;

        for (Video v : videos) {
            if (v.getVideo_views() != null) totalViews += v.getVideo_views();
            if (v.getLikesCount() != null) totalLikes += v.getLikesCount();
        }

        // 2. Fetch subscriber count
        long totalSubscribers = subscriptionRepository.countByChannelUserId(uid);

        // 3. Top 5 performing videos
        List<VideoDTO> topVideos = videos.stream()
                .limit(5)
                .map(videoDTOMapper::convertEntityToDTO)
                .toList();

        return CreatorAnalyticsDTO.builder()
                .creatorUserId(uid)
                .totalVideos(totalVideos)
                .totalViews(totalViews)
                .totalLikes(totalLikes)
                .totalSubscribers(totalSubscribers)
                .topVideos(topVideos)
                .build();
    }
}
