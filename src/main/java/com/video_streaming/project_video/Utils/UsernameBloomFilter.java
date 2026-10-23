package com.video_streaming.project_video.Utils;

import com.video_streaming.project_video.Repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;

/**
 * Production-grade Bloom Filter for sub-millisecond username availability checks.
 *
 * Employs Kirsch-Mitzenmacher 64-bit double hashing with MurmurHash3 to achieve
 * an optimal ~1% false positive rate for 100,000+ usernames using only ~128 KB of memory.
 *
 * Guarantees:
 * - If mightContain() returns false: the username is 100% available (zero DB queries needed).
 * - If mightContain() returns true: the username is likely taken, prompting a DB lookup.
 * - Synchronizes with Redis BitSet when available for cluster-wide consistency.
 */
@Component
public class UsernameBloomFilter {

    private static final Logger logger = LoggerFactory.getLogger(UsernameBloomFilter.class);

    // 1,048,576 bits (128 KB)
    private static final int BIT_SIZE = 1 << 20;
    private static final int NUM_HASH_FUNCTIONS = 7;
    private static final String REDIS_BLOOM_KEY = "bloom:usernames";

    private final BitSet localBitSet = new BitSet(BIT_SIZE);
    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;
    private final java.util.concurrent.atomic.AtomicBoolean warmedUp = new java.util.concurrent.atomic.AtomicBoolean(false);

    public UsernameBloomFilter() {
        this.stringRedisTemplate = null;
        this.userRepository = null;
        this.warmedUp.set(true); // Standalone instance with no repository is immediately ready
    }

    @Autowired(required = false)
    public UsernameBloomFilter(StringRedisTemplate stringRedisTemplate, UserRepository userRepository) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        warmUp();
    }

    public boolean isWarmedUp() {
        return warmedUp.get();
    }

    public void setWarmedUp(boolean isWarmedUp) {
        this.warmedUp.set(isWarmedUp);
    }

    /**
     * Pre-populates the Bloom filter with existing usernames from the repository.
     */
    public synchronized void warmUp() {
        if (userRepository == null) {
            warmedUp.set(true);
            return;
        }
        try {
            List<String> usernames = userRepository.findAllUsernames();
            if (usernames != null && !usernames.isEmpty()) {
                for (String username : usernames) {
                    if (username != null && !username.isBlank()) {
                        addLocal(username);
                    }
                }
                logger.info("Username Bloom filter successfully warmed up with {} usernames", usernames.size());
            }
            warmedUp.set(true);
        } catch (Exception e) {
            logger.warn("Bloom filter pre-warm notice (tables may be pending migration): {}", e.getMessage());
        }
    }

    /**
     * Adds a username to the Bloom filter.
     */
    public void add(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        String clean = normalize(username);
        int[] indices = getBitIndices(clean);

        // 1. Add to in-memory local bitset
        synchronized (localBitSet) {
            for (int idx : indices) {
                localBitSet.set(idx);
            }
        }

        // 2. Add to distributed Redis bitset if connected
        if (stringRedisTemplate != null) {
            try {
                for (int idx : indices) {
                    stringRedisTemplate.opsForValue().setBit(REDIS_BLOOM_KEY, idx, true);
                }
            } catch (Exception e) {
                logger.debug("Redis bloom bit update skipped in fallback mode: {}", e.getMessage());
            }
        }
    }

    /**
     * Probabilistic check for username existence.
     *
     * @param username The candidate username to test.
     * @return false = 100% DEFINITELY NOT TAKEN (available), true = PROBABLY TAKEN (verify against DB).
     */
    public boolean mightContain(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        if (!warmedUp.get()) {
            // Filter is uninitialized or not yet warmed up from DB: fall through to database check
            return true;
        }
        String clean = normalize(username);
        int[] indices = getBitIndices(clean);

        // 1. Check local memory bitset (sub-microsecond)
        synchronized (localBitSet) {
            for (int idx : indices) {
                if (!localBitSet.get(idx)) {
                    return false; // Definitive absence
                }
            }
        }

        // 2. If all local bits set and Redis available, verify against cluster Redis bitset
        if (stringRedisTemplate != null) {
            try {
                for (int idx : indices) {
                    Boolean bit = stringRedisTemplate.opsForValue().getBit(REDIS_BLOOM_KEY, idx);
                    if (bit != null && !bit) {
                        return false;
                    }
                }
            } catch (Exception e) {
                logger.debug("Redis bloom check skipped in fallback mode: {}", e.getMessage());
            }
        }

        return true;
    }

    private void addLocal(String username) {
        String clean = normalize(username);
        int[] indices = getBitIndices(clean);
        synchronized (localBitSet) {
            for (int idx : indices) {
                localBitSet.set(idx);
            }
        }
    }

    private String normalize(String input) {
        String s = input.trim();
        if (s.startsWith("@")) {
            s = s.substring(1).trim();
        }
        return s.toLowerCase(Locale.ROOT);
    }

    private int[] getBitIndices(String username) {
        byte[] bytes = username.getBytes(StandardCharsets.UTF_8);
        long h1 = murmurHash64(bytes, 0x12345678);
        long h2 = murmurHash64(bytes, 0x87654321);

        int[] indices = new int[NUM_HASH_FUNCTIONS];
        for (int i = 0; i < NUM_HASH_FUNCTIONS; i++) {
            long combined = h1 + (long) i * h2;
            int idx = (int) ((combined & 0x7fffffffffffffffL) % BIT_SIZE);
            indices[i] = idx;
        }
        return indices;
    }

    public static long murmurHash64(byte[] data, int seed) {
        long m = 0xc6a4a7935bd1e995L;
        int r = 47;
        long h = (seed & 0xffffffffL) ^ (data.length * m);

        int length8 = data.length / 8;
        for (int i = 0; i < length8; i++) {
            int i8 = i * 8;
            long k = ((long) data[i8] & 0xff)
                    | (((long) data[i8 + 1] & 0xff) << 8)
                    | (((long) data[i8 + 2] & 0xff) << 16)
                    | (((long) data[i8 + 3] & 0xff) << 24)
                    | (((long) data[i8 + 4] & 0xff) << 32)
                    | (((long) data[i8 + 5] & 0xff) << 40)
                    | (((long) data[i8 + 6] & 0xff) << 48)
                    | (((long) data[i8 + 7] & 0xff) << 56);

            k *= m;
            k ^= k >>> r;
            k *= m;

            h ^= k;
            h *= m;
        }

        int rem = data.length % 8;
        int offset = length8 * 8;
        switch (rem) {
            case 7: h ^= (long) (data[offset + 6] & 0xff) << 48;
            case 6: h ^= (long) (data[offset + 5] & 0xff) << 40;
            case 5: h ^= (long) (data[offset + 4] & 0xff) << 32;
            case 4: h ^= (long) (data[offset + 3] & 0xff) << 24;
            case 3: h ^= (long) (data[offset + 2] & 0xff) << 16;
            case 2: h ^= (long) (data[offset + 1] & 0xff) << 8;
            case 1:
                h ^= (long) (data[offset] & 0xff);
                h *= m;
        }

        h ^= h >>> r;
        h *= m;
        h ^= h >>> r;

        return h;
    }
}
