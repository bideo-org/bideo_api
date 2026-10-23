package com.video_streaming.project_video.ServiceImplementation;

import com.video_streaming.project_video.DTOMapper.VideoDTOMapper;
import com.video_streaming.project_video.DTOs.VideoDTO;
import com.video_streaming.project_video.Entity.Video;
import com.video_streaming.project_video.Enums.VideoStatus;
import com.video_streaming.project_video.Enums.VideoVisibility;
import com.video_streaming.project_video.Repository.VideoRepository;
import com.video_streaming.project_video.Service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DiscoveryServiceImpl implements DiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryServiceImpl.class);
    private static final String SUGGEST_PREFIX = "search:suggest:";

    private final VideoRepository videoRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final VideoDTOMapper videoDTOMapper = new VideoDTOMapper();

    @Override
    @Transactional(readOnly = true)
    public Page<VideoDTO> getRelatedVideos(Long videoId, Pageable pageable) {
        if (videoId == null) {
            throw new IllegalArgumentException("Video ID is required");
        }

        Video currentVideo = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found with ID: " + videoId));

        String uploaderId = currentVideo.getVideo_uploader() != null
                ? currentVideo.getVideo_uploader().getUserId()
                : null;

        List<String> currentTags = currentVideo.getTags() != null ? currentVideo.getTags() : Collections.emptyList();
        Set<String> currentTagsLower = new HashSet<>();
        for (String t : currentTags) {
            if (t != null) currentTagsLower.add(t.trim().toLowerCase());
        }

        Map<Long, Video> candidateMap = new LinkedHashMap<>();

        // 1. Tag matches
        if (!currentTagsLower.isEmpty()) {
            List<String> tagsList = new ArrayList<>(currentTagsLower);
            Page<Video> tagMatches = videoRepository.findRelatedByTags(videoId, tagsList, PageRequest.of(0, 30));
            for (Video v : tagMatches.getContent()) {
                candidateMap.put(v.getVideoId(), v);
            }
        }

        // 2. Category & channel matches
        Page<Video> catOrUploaderMatches = videoRepository.findRelatedByCategoryOrUploader(
                videoId, currentVideo.getCategory(), uploaderId, PageRequest.of(0, 30)
        );
        for (Video v : catOrUploaderMatches.getContent()) {
            candidateMap.put(v.getVideoId(), v);
        }

        // 3. Backfill if candidate pool is small
        int requestedSize = pageable.getPageSize();
        if (candidateMap.size() < requestedSize) {
            Page<Video> fallback = videoRepository.findByVisibilityAndVideoStatus(
                    VideoVisibility.PUBLIC, VideoStatus.COMPLETED, PageRequest.of(0, requestedSize)
            );
            for (Video v : fallback.getContent()) {
                if (!v.getVideoId().equals(videoId)) {
                    candidateMap.putIfAbsent(v.getVideoId(), v);
                }
            }
        }

        // 4. Rank candidates by composite engagement and relevance score
        List<Video> scoredList = new ArrayList<>(candidateMap.values());
        scoredList.sort((a, b) -> {
            double scoreA = calculateRelevanceScore(a, currentVideo, uploaderId, currentTagsLower);
            double scoreB = calculateRelevanceScore(b, currentVideo, uploaderId, currentTagsLower);
            return Double.compare(scoreB, scoreA);
        });

        // 5. Paginate
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), scoredList.size());

        List<VideoDTO> pageContent;
        if (start >= scoredList.size()) {
            pageContent = Collections.emptyList();
        } else {
            pageContent = scoredList.subList(start, end).stream()
                    .map(videoDTOMapper::convertEntityToDTO)
                    .toList();
        }

        return new PageImpl<>(pageContent, pageable, scoredList.size());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VideoDTO> getTrendingVideos(Pageable pageable) {
        // Fetch candidates with high views or recent activity
        Page<Video> candidates = videoRepository.findPopularVideos(PageRequest.of(0, 100));
        List<Video> videoList = new ArrayList<>(candidates.getContent());

        long now = System.currentTimeMillis();

        // Rank by HackerNews / Reddit style velocity decay formula:
        // Score = (views * 1.5 + likes * 3.0 + 1) / (hoursAge + 2)^1.5
        videoList.sort((a, b) -> {
            double scoreA = calculateTrendingScore(a, now);
            double scoreB = calculateTrendingScore(b, now);
            return Double.compare(scoreB, scoreA);
        });

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), videoList.size());

        List<VideoDTO> pageContent;
        if (start >= videoList.size()) {
            pageContent = Collections.emptyList();
        } else {
            pageContent = videoList.subList(start, end).stream()
                    .map(videoDTOMapper::convertEntityToDTO)
                    .toList();
        }

        return new PageImpl<>(pageContent, pageable, videoList.size());
    }

    @Override
    public List<String> getSearchSuggestions(String prefix, int limit) {
        if (prefix == null || prefix.trim().isBlank()) {
            return Collections.emptyList();
        }

        String cleanPrefix = prefix.trim().toLowerCase();
        int safeLimit = Math.max(1, Math.min(limit, 20));

        // Check Redis cache
        String cacheKey = SUGGEST_PREFIX + cleanPrefix + ":" + safeLimit;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                return Arrays.asList(cached.split("\\|\\|"));
            }
        } catch (Exception e) {
            logger.warn("Redis suggestion cache lookup failed: {}", e.getMessage());
        }

        Pageable queryPageable = PageRequest.of(0, safeLimit);
        List<String> titleSuggestions = videoRepository.findTitleSuggestions(cleanPrefix, queryPageable);
        List<String> tagSuggestions = videoRepository.findTagSuggestions(cleanPrefix, queryPageable);

        Set<String> uniqueSuggestions = new LinkedHashSet<>();
        if (titleSuggestions != null) uniqueSuggestions.addAll(titleSuggestions);
        if (tagSuggestions != null) uniqueSuggestions.addAll(tagSuggestions);

        List<String> result = uniqueSuggestions.stream().limit(safeLimit).toList();

        // Cache in Redis for 10 minutes
        try {
            if (!result.isEmpty()) {
                String serialized = String.join("||", result);
                stringRedisTemplate.opsForValue().set(cacheKey, serialized, Duration.ofMinutes(10));
            }
        } catch (Exception e) {
            logger.warn("Redis suggestion caching failed: {}", e.getMessage());
        }

        return result;
    }

    private double calculateRelevanceScore(Video candidate, Video currentVideo, String uploaderId, Set<String> currentTagsLower) {
        double score = 0.0;

        // Tag overlap (highest weight)
        if (candidate.getTags() != null) {
            for (String t : candidate.getTags()) {
                if (t != null && currentTagsLower.contains(t.trim().toLowerCase())) {
                    score += 3.0;
                }
            }
        }

        // Same creator
        if (uploaderId != null && candidate.getVideo_uploader() != null && uploaderId.equals(candidate.getVideo_uploader().getUserId())) {
            score += 2.0;
        }

        // Same category
        if (candidate.getCategory() == currentVideo.getCategory()) {
            score += 1.5;
        }

        // Engagement dampener
        long views = candidate.getVideo_views() != null ? candidate.getVideo_views() : 0L;
        score += Math.log10(views + 1.0);

        return score;
    }

    private double calculateTrendingScore(Video video, long now) {
        long views = video.getVideo_views() != null ? video.getVideo_views() : 0L;
        long likes = video.getLikesCount() != null ? video.getLikesCount() : 0L;

        long uploadTime = video.getVideo_uploadDate() != null ? video.getVideo_uploadDate().getTime() : now;
        double hoursAge = Math.max(0.0, (now - uploadTime) / 3600000.0);

        return (views * 1.5 + likes * 3.0 + 1.0) / Math.pow(hoursAge + 2.0, 1.5);
    }
}
