package com.video_streaming.project_video.Utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UsernameBloomFilterTest {

    private UsernameBloomFilter bloomFilter;

    @BeforeEach
    void setUp() {
        // Standalone in-memory instance
        bloomFilter = new UsernameBloomFilter();
    }

    @Test
    @DisplayName("Should return false for unadded usernames (zero false negatives)")
    void testAbsentUsernames() {
        assertFalse(bloomFilter.mightContain("completely_random_user_12345"));
        assertFalse(bloomFilter.mightContain("another_nonexistent_handle"));
        assertFalse(bloomFilter.mightContain(null));
        assertFalse(bloomFilter.mightContain(""));
        assertFalse(bloomFilter.mightContain("   "));
    }

    @Test
    @DisplayName("Should detect added usernames accurately")
    void testAddedUsernames() {
        bloomFilter.add("buzzcreator");
        assertTrue(bloomFilter.mightContain("buzzcreator"));
    }

    @Test
    @DisplayName("Should be case-insensitive and tolerate @ prefix")
    void testCaseAndPrefixNormalization() {
        bloomFilter.add("TechReviewer");

        assertTrue(bloomFilter.mightContain("techreviewer"));
        assertTrue(bloomFilter.mightContain("TECHREVIEWER"));
        assertTrue(bloomFilter.mightContain("@TechReviewer"));
        assertTrue(bloomFilter.mightContain("@techreviewer"));

        // Adding with @ should also match without @
        bloomFilter.add("@SuperGamer");
        assertTrue(bloomFilter.mightContain("supergamer"));
        assertTrue(bloomFilter.mightContain("@SuperGamer"));
    }

    @Test
    @DisplayName("Should maintain low false positive rate (<1%) under load")
    void testFalsePositiveRate() {
        int insertedCount = 1000;
        for (int i = 0; i < insertedCount; i++) {
            bloomFilter.add("user_" + i);
        }

        // All inserted must be found (0% false negative)
        for (int i = 0; i < insertedCount; i++) {
            assertTrue(bloomFilter.mightContain("user_" + i));
        }

        // Test 1000 uninserted random usernames
        int falsePositives = 0;
        int testCount = 1000;
        for (int i = 0; i < testCount; i++) {
            String randomHandle = "uninserted_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            if (bloomFilter.mightContain(randomHandle)) {
                falsePositives++;
            }
        }

        double fpRate = (double) falsePositives / testCount;
        assertTrue(fpRate < 0.05, "False positive rate should be well below 5%, got: " + fpRate);
    }
}
