package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.DTOs.VideoDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DiscoveryService {

    /**
     * Recommends up-next videos relevant to the given video ID based on tag overlap,
     * category alignment, channel uploader, and engagement score.
     */
    Page<VideoDTO> getRelatedVideos(Long videoId, Pageable pageable);

    /**
     * Computes trending videos ranked by engagement velocity with logarithmic age decay.
     */
    Page<VideoDTO> getTrendingVideos(Pageable pageable);

    /**
     * Fast prefix search autocomplete suggesting titles and tags for search dropdown.
     */
    List<String> getSearchSuggestions(String prefix, int limit);
}
