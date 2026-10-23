# 🚀 Frontend Integration Guide for Video SEO & Metadata

This guide provides instructions and copy-pasteable TypeScript/React code snippets to integrate the new backend SEO metadata, slug-based routing, video upload/edit features, and search in the frontend application (`bideo_ui`).

---

## 1. Domain & API Endpoints

- **Production Domain**: `https://buzzclips.app`
- **Backend API Base**: `https://buzzclips.app/api` (or `http://localhost:8085/api` during local development)

### Available API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/vid/slug/{slug}` | **Primary Video Lookup** (returns video + precomputed JSON-LD & meta tags) |
| `GET` | `/api/vid/view/{videoId}` | Lookup by numeric ID (increments views) |
| `POST` | `/api/vid/upload` | Multipart upload with optional `description`, `tags`, `category`, `visibility` |
| `PUT` | `/api/vid/{videoId}?userId={userId}` | Edit video title, description, tags, category, visibility |
| `GET` | `/api/vid/search?query=&category=&tag=&page=&size=` | Search & filter public completed videos |
| `GET` | `/api/vid/all?page=&size=` | Public completed videos feed |

---

## 2. TypeScript Interfaces

Add or update your video type definitions in `src/types/video.ts` (or `global.ts`):

```typescript
export type VideoCategory =
  | 'TECH'
  | 'EDUCATION'
  | 'GAMING'
  | 'ENTERTAINMENT'
  | 'MUSIC'
  | 'NEWS'
  | 'SPORTS'
  | 'LIFESTYLE'
  | 'OTHER';

export type VideoVisibility = 'PUBLIC' | 'UNLISTED' | 'PRIVATE';

export type VideoStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface SeoMetadataDTO {
  title: string;
  description: string;
  canonicalUrl: string;
  jsonLdVideoObject: Record<string, any>;
  openGraph: Record<string, string>;
  twitterCard: Record<string, string>;
}

export interface UserDTO {
  userId: string;
  user_name: string;
  user_email?: string;
  user_role?: string;
  avatar_url?: string;
}

export interface VideoDTO {
  videoId: number;
  video_title: string;
  slug: string;
  description?: string;
  tags?: string[];
  category?: VideoCategory;
  visibility?: VideoVisibility;
  video_uploadDate: string;
  video_views: number;
  m3u8Url?: string;
  video_duration?: string;
  iso_duration?: string;
  thumbnail_url?: string;
  videoStatus: VideoStatus;
  video_uploader?: UserDTO;
  seo?: SeoMetadataDTO;
}

export interface VideoMetadataUpdateRequest {
  videoTitle?: string;
  description?: string;
  tags?: string[];
  category?: VideoCategory;
  visibility?: VideoVisibility;
}
```

---

## 3. SEO Head & JSON-LD Component (`VideoSeoHead.tsx`)

Whether using **React Helmet**, **Next.js (`<Head>`)**, or native DOM manipulation, use the precomputed `video.seo` object:

```tsx
import React, { useEffect } from 'react';
import { VideoDTO } from '../types/video';

interface VideoSeoHeadProps {
  video: VideoDTO;
}

export const VideoSeoHead: React.FC<VideoSeoHeadProps> = ({ video }) => {
  const seo = video.seo;

  useEffect(() => {
    if (!seo) return;

    // Document title
    document.title = `${seo.title} | BuzzClips`;

    // Helper to set or create meta tags
    const setMetaTag = (property: string, content: string, isName = false) => {
      const selector = isName ? `meta[name="${property}"]` : `meta[property="${property}"]`;
      let element = document.querySelector(selector);
      if (!element) {
        element = document.createElement('meta');
        if (isName) element.setAttribute('name', property);
        else element.setAttribute('property', property);
        document.head.appendChild(element);
      }
      element.setAttribute('content', content);
    };

    // Standard meta
    setMetaTag('description', seo.description, true);

    // OpenGraph
    if (seo.openGraph) {
      Object.entries(seo.openGraph).forEach(([key, value]) => {
        setMetaTag(key, value);
      });
    }

    // Twitter Cards
    if (seo.twitterCard) {
      Object.entries(seo.twitterCard).forEach(([key, value]) => {
        setMetaTag(key, value, true);
      });
    }

    // Canonical link
    let canonical = document.querySelector('link[rel="canonical"]');
    if (!canonical) {
      canonical = document.createElement('link');
      canonical.setAttribute('rel', 'canonical');
      document.head.appendChild(canonical);
    }
    canonical.setAttribute('href', seo.canonicalUrl);

    // Schema.org JSON-LD VideoObject script tag
    const scriptId = 'video-schema-jsonld';
    let scriptTag = document.getElementById(scriptId) as HTMLScriptElement;
    if (!scriptTag) {
      scriptTag = document.createElement('script');
      scriptTag.id = scriptId;
      scriptTag.type = 'application/ld+json';
      document.head.appendChild(scriptTag);
    }
    scriptTag.text = JSON.stringify(seo.jsonLdVideoObject);

    return () => {
      // Optional cleanup on unmount
      const existingScript = document.getElementById(scriptId);
      if (existingScript) existingScript.remove();
    };
  }, [seo]);

  return null;
};
```

---

## 4. Slug-Based Video Watch Page (`WatchPage.tsx`)

Update your router (e.g. React Router) to route `/watch/:slug`:

```tsx
import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import axios from 'axios';
import { VideoDTO } from '../types/video';
import { VideoSeoHead } from '../components/VideoSeoHead';

export const WatchPage: React.FC = () => {
  const { slug } = useParams<{ slug: string }>();
  const [video, setVideo] = useState<VideoDTO | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!slug) return;
    setLoading(true);

    // Fetches by slug directly
    axios.get<VideoDTO>(`/api/vid/slug/${slug}`)
      .then(res => setVideo(res.data))
      .catch(err => {
        console.error("Failed to load video", err);
      })
      .finally(() => setLoading(false));
  }, [slug]);

  if (loading) return <div>Loading video...</div>;
  if (!video) return <div>Video not found</div>;

  return (
    <div className="watch-container">
      {/* Dynamic SEO Meta Tags & Schema.org JSON-LD */}
      <VideoSeoHead video={video} />

      <h1>{video.video_title}</h1>
      {/* Video Player (HLS) */}
      <video controls src={video.m3u8Url} poster={video.thumbnail_url} style={{ width: '100%', maxWidth: 960 }} />

      <div className="video-metadata">
        <span>{video.video_views} views</span> • <span>{new Date(video.video_uploadDate).toLocaleDateString()}</span>
        <p>{video.description}</p>
        <div className="tags">
          {video.tags?.map(tag => (
            <span key={tag} className="tag-pill">#{tag}</span>
          ))}
        </div>
      </div>
    </div>
  );
};
```

---

## 5. Upload Video with Full Metadata

Update your upload form to send `description`, `tags`, `category`, and `visibility`:

```typescript
export const uploadVideoWithMetadata = async (
  file: File,
  title: string,
  userId: string,
  description?: string,
  tags?: string[],
  category: string = 'OTHER',
  visibility: string = 'PUBLIC'
) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('videoTitle', title);
  formData.append('userId', userId);
  if (description) formData.append('description', description);
  if (tags && tags.length > 0) {
    tags.forEach(tag => formData.append('tags', tag));
  }
  formData.append('category', category);
  formData.append('visibility', visibility);

  const response = await axios.post('/api/vid/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });

  return response.data;
};
```

---

## 6. Edit Video Metadata API

Provide an edit modal on the creator's video page:

```typescript
export const updateVideoMetadata = async (
  videoId: number,
  userId: string,
  payload: VideoMetadataUpdateRequest
) => {
  const response = await axios.put<VideoDTO>(
    `/api/vid/${videoId}?userId=${encodeURIComponent(userId)}`,
    payload
  );
  return response.data;
};
```

---

## 7. Video Search & Discovery API

To implement search and category filtering:

```typescript
export const searchVideos = async (
  query?: string,
  category?: string,
  tag?: string,
  page = 0,
  size = 10
) => {
  const params = new URLSearchParams();
  if (query) params.append('query', query);
  if (category && category !== 'ALL') params.append('category', category);
  if (tag) params.append('tag', tag);
  params.append('page', page.toString());
  params.append('size', size.toString());

  const response = await axios.get(`/api/vid/search?${params.toString()}`);
  return response.data; // Page<VideoDTO>
};
```

---

## 8. Video Likes & Dislikes (Redis-Backed O(1))

### API Endpoints
- `GET /api/vid/{videoId}/engagement` (Public / Optional Auth): Returns `{ videoId, likesCount, dislikesCount, userReaction }`
- `POST /api/vid/{videoId}/like` (Bearer Token): Toggles like (or switches from dislike)
- `POST /api/vid/{videoId}/dislike` (Bearer Token): Toggles dislike (or switches from like)

### React Component Snippet (`EngagementButtons.tsx`):
```tsx
import React, { useEffect, useState } from 'react';
import axios from 'axios';

interface EngagementDTO {
  videoId: number;
  likesCount: number;
  dislikesCount: number;
  userReaction: 'LIKE' | 'DISLIKE' | 'NONE';
}

export const EngagementButtons: React.FC<{ videoId: number; token?: string }> = ({ videoId, token }) => {
  const [data, setData] = useState<EngagementDTO | null>(null);

  useEffect(() => {
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    axios.get<EngagementDTO>(`/api/vid/${videoId}/engagement`, { headers })
      .then(res => setData(res.data))
      .catch(console.error);
  }, [videoId, token]);

  const handleLike = async () => {
    if (!token) return alert('Please sign in to like');
    const res = await axios.post<EngagementDTO>(`/api/vid/${videoId}/like`, {}, {
      headers: { Authorization: `Bearer ${token}` }
    });
    setData(res.data);
  };

  const handleDislike = async () => {
    if (!token) return alert('Please sign in to dislike');
    const res = await axios.post<EngagementDTO>(`/api/vid/${videoId}/dislike`, {}, {
      headers: { Authorization: `Bearer ${token}` }
    });
    setData(res.data);
  };

  if (!data) return null;

  return (
    <div className="flex items-center gap-2 bg-gray-100 dark:bg-gray-800 rounded-full px-4 py-1.5 w-fit">
      <button
        onClick={handleLike}
        className={`flex items-center gap-1.5 font-medium ${data.userReaction === 'LIKE' ? 'text-blue-600' : 'text-gray-700 dark:text-gray-200'}`}
      >
        👍 <span>{data.likesCount}</span>
      </button>
      <span className="text-gray-400">|</span>
      <button
        onClick={handleDislike}
        className={`flex items-center gap-1.5 font-medium ${data.userReaction === 'DISLIKE' ? 'text-red-500' : 'text-gray-700 dark:text-gray-200'}`}
      >
        👎 <span>{data.dislikesCount}</span>
      </button>
    </div>
  );
};
```

---

## 9. Threaded Comments Component with Timestamp Seeking

### API Endpoints
- `GET /api/vid/{videoId}/comments?sort=newest|top&page=&size=` (Public read)
- `GET /api/vid/comments/{commentId}/replies` (Public read)
- `POST /api/vid/{videoId}/comments` (Authenticated) `{ content: string, parentCommentId?: number, timestampInSeconds?: number }`
- `DELETE /api/vid/comments/{commentId}` (Author or Video Creator)
- `POST /api/vid/comments/{commentId}/pin` (Video Creator only)

### React Component Snippet (`CommentsSection.tsx`):
```tsx
import React, { useEffect, useState } from 'react';
import axios from 'axios';

export interface CommentDTO {
  commentId: number;
  content: string;
  timestampInSeconds?: number;
  likeCount: number;
  isPinned: boolean;
  replyCount: number;
  createdAt: string;
  user: {
    userId: string;
    user_name: string;
    avatar_url?: string;
  };
}

export const CommentsSection: React.FC<{
  videoId: number;
  token?: string;
  onSeekTo?: (seconds: number) => void;
}> = ({ videoId, token, onSeekTo }) => {
  const [comments, setComments] = useState<CommentDTO[]>([]);
  const [newComment, setNewComment] = useState('');

  const fetchComments = () => {
    axios.get(`/api/vid/${videoId}/comments?sort=newest`)
      .then(res => setComments(res.data.content || []))
      .catch(console.error);
  };

  useEffect(() => {
    fetchComments();
  }, [videoId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newComment.trim() || !token) return;

    await axios.post(
      `/api/vid/${videoId}/comments`,
      { content: newComment },
      { headers: { Authorization: `Bearer ${token}` } }
    );
    setNewComment('');
    fetchComments();
  };

  return (
    <div className="mt-8 space-y-4">
      <h3 className="text-xl font-bold">{comments.length} Comments</h3>
      {token && (
        <form onSubmit={handleSubmit} className="flex gap-2">
          <input
            type="text"
            value={newComment}
            onChange={e => setNewComment(e.target.value)}
            placeholder="Add a comment... (Tip: include 01:23 for video timestamps!)"
            className="flex-1 px-4 py-2 border rounded-lg dark:bg-gray-800"
          />
          <button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded-lg">Comment</button>
        </form>
      )}

      <div className="space-y-4 mt-6">
        {comments.map(c => (
          <div key={c.commentId} className={`p-4 rounded-xl border ${c.isPinned ? 'border-amber-400 bg-amber-50/20' : 'border-gray-200'}`}>
            <div className="flex items-center gap-2 mb-1">
              <img src={c.user?.avatar_url || '/default-avatar.png'} alt="" className="w-6 h-6 rounded-full" />
              <span className="font-semibold text-sm">{c.user?.user_name}</span>
              {c.isPinned && <span className="text-xs bg-amber-100 text-amber-800 px-2 py-0.5 rounded">📌 Pinned by creator</span>}
            </div>
            <p className="text-gray-800 dark:text-gray-200 text-sm">
              {c.content}
              {c.timestampInSeconds != null && onSeekTo && (
                <button
                  type="button"
                  onClick={() => onSeekTo(c.timestampInSeconds!)}
                  className="ml-2 text-blue-500 font-mono underline hover:text-blue-700"
                >
                  ▶ Jump to {Math.floor(c.timestampInSeconds / 60)}:{(c.timestampInSeconds % 60).toString().padStart(2, '0')}
                </button>
              )}
            </p>
          </div>
        ))}
      </div>
    </div>
  );
};
```

---

## 10. Cross-Device Resume Playback Hook (`useResumePlayback.ts`)

```typescript
import { useEffect, useRef } from 'react';
import axios from 'axios';

export function useResumePlayback(
  videoId: number,
  videoRef: React.RefObject<HTMLVideoElement>,
  token?: string
) {
  const lastHeartbeat = useRef(0);

  // 1. Fetch saved progress on mount
  useEffect(() => {
    if (!token || !videoId) return;

    axios.get(`/api/vid/${videoId}/progress`, {
      headers: { Authorization: `Bearer ${token}` }
    }).then(res => {
      const savedSeconds = res.data?.progressSeconds || 0;
      if (savedSeconds > 5 && !res.data?.completed && videoRef.current) {
        videoRef.current.currentTime = savedSeconds;
      }
    }).catch(console.error);
  }, [videoId, token]);

  // 2. Heartbeat progress every 10 seconds during playback
  const onTimeUpdate = () => {
    if (!token || !videoRef.current) return;
    const now = Date.now();
    if (now - lastHeartbeat.current > 10000) {
      lastHeartbeat.current = now;
      const current = videoRef.current.currentTime;
      const duration = videoRef.current.duration;
      axios.post(`/api/vid/${videoId}/progress?seconds=${current}&duration=${duration}`, {}, {
        headers: { Authorization: `Bearer ${token}` }
      }).catch(() => {});
    }
  };

  return { onTimeUpdate };
}
```

---

## 11. Channel Subscriptions & Follow Feed

### API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/channel/{channelId}/subscribe` | Toggle subscribe/unsubscribe (Requires Auth) |
| `GET` | `/api/channel/{channelId}/status` | Check subscription status and subscriber count (Optional Auth) |
| `GET` | `/api/channel/{channelId}/subscribers` | Get public subscriber count |
| `GET` | `/api/feed/subscriptions?page=0&size=20` | Get chronological video feed from subscribed channels (Requires Auth) |

### Subscribe Button Component (`SubscribeButton.tsx`)

```tsx
import React, { useState, useEffect } from 'react';
import axios from 'axios';

interface SubscribeButtonProps {
  channelId: string;
  token?: string;
}

export const SubscribeButton: React.FC<SubscribeButtonProps> = ({ channelId, token }) => {
  const [isSubscribed, setIsSubscribed] = useState(false);
  const [subscriberCount, setSubscriberCount] = useState(0);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    axios.get(`/api/channel/${channelId}/status`, { headers })
      .then(res => {
        setIsSubscribed(res.data.subscribed);
        setSubscriberCount(res.data.subscriberCount);
      })
      .catch(console.error);
  }, [channelId, token]);

  const handleToggle = async () => {
    if (!token) {
      alert("Please sign in to subscribe");
      return;
    }
    setLoading(true);
    try {
      const res = await axios.post(`/api/channel/${channelId}/subscribe`, {}, {
        headers: { Authorization: `Bearer ${token}` }
      });
      setIsSubscribed(res.data.subscribed);
      setSubscriberCount(res.data.subscriberCount);
    } catch (err: any) {
      alert(err.response?.data?.message || "Failed to update subscription");
    } finally {
      setLoading(false);
    }
  };

  return (
    <button
      onClick={handleToggle}
      disabled={loading}
      className={`px-4 py-2 rounded-full font-semibold transition ${
        isSubscribed
          ? 'bg-neutral-800 text-neutral-300 hover:bg-neutral-700'
          : 'bg-white text-black hover:bg-neutral-200'
      }`}
    >
      {isSubscribed ? 'Subscribed' : 'Subscribe'} ({subscriberCount.toLocaleString()})
    </button>
  );
};
```

---

## 12. Playlists & Watch Later

### API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/playlists` | Create custom playlist (`{ title, description, visibility }`) |
| `GET` | `/api/playlists/my` | Get current user's playlists (Auto-provisions Watch Later) |
| `GET` | `/api/playlists/{playlistId}` | Get playlist details with ordered video list |
| `POST` | `/api/playlists/{playlistId}/videos/{videoId}` | Add video to playlist |
| `DELETE` | `/api/playlists/{playlistId}/videos/{videoId}` | Remove video from playlist |
| `DELETE` | `/api/playlists/{playlistId}` | Delete playlist |
| `POST` | `/api/playlists/watch-later/{videoId}` | Toggle video in Watch Later |

---

## 13. Creator Studio Analytics

### API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/creator/analytics` | Get current authenticated creator's studio metrics (Requires Auth) |
| `GET` | `/api/creator/{creatorId}/analytics` | Get public creator channel overview |

### Response Schema (`CreatorAnalyticsDTO`)

```json
{
  "creatorUserId": "firebase-uid-123",
  "totalVideos": 14,
  "totalViews": 245000,
  "totalLikes": 18200,
  "totalSubscribers": 5420,
  "topVideos": [
    {
      "videoId": 105,
      "video_title": "BuzzClips Launch Masterclass",
      "video_views": 120500,
      "likesCount": 9400
    }
  ]
}
```

---

## 14. Discovery, Trending & Search Autocomplete

### API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/vid/{videoId}/related?page=0&size=10` | "Up Next" / Related videos scored by tag overlap, category, and creator |
| `GET` | `/api/vid/trending?page=0&size=20` | Dynamic velocity-decay trending feed |
| `GET` | `/api/vid/search/suggest?q={prefix}&limit=8` | Sub-5ms instant autocomplete suggestions for search bar |

### Search Autocomplete Dropdown Component (`SearchAutocomplete.tsx`)

```tsx
import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';

interface SearchAutocompleteProps {
  onSelectSuggestion: (query: string) => void;
}

export const SearchAutocomplete: React.FC<SearchAutocompleteProps> = ({ onSelectSuggestion }) => {
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [isOpen, setIsOpen] = useState(false);
  const debounceTimer = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    if (!query.trim()) {
      setSuggestions([]);
      setIsOpen(false);
      return;
    }

    if (debounceTimer.current) clearTimeout(debounceTimer.current);

    debounceTimer.current = setTimeout(async () => {
      try {
        const res = await axios.get<string[]>(`/api/vid/search/suggest`, {
          params: { q: query.trim(), limit: 6 }
        });
        setSuggestions(res.data);
        setIsOpen(res.data.length > 0);
      } catch (err) {
        console.error("Autocomplete error:", err);
      }
    }, 150);

    return () => {
      if (debounceTimer.current) clearTimeout(debounceTimer.current);
    };
  }, [query]);

  return (
    <div className="relative w-full max-w-lg">
      <input
        type="text"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search BuzzClips..."
        className="w-full px-4 py-2 bg-neutral-900 text-white rounded-full border border-neutral-700 focus:outline-none focus:border-blue-500"
      />
      {isOpen && (
        <ul className="absolute z-50 left-0 right-0 mt-2 bg-neutral-900 border border-neutral-800 rounded-2xl shadow-xl overflow-hidden">
          {suggestions.map((item, idx) => (
            <li
              key={idx}
              onClick={() => {
                setQuery(item);
                setIsOpen(false);
                onSelectSuggestion(item);
              }}
              className="px-4 py-2.5 text-neutral-200 hover:bg-neutral-800 cursor-pointer flex items-center gap-2"
            >
              <span className="text-neutral-500 text-sm">🔍</span>
              <span>{item}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};
```



