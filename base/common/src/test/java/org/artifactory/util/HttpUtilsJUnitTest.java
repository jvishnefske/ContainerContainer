package org.artifactory.util;

import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpUtilsJUnitTest extends ArtifactoryHomeBoundJUnitTest {
    @Test
    void adjustRefererValueUsesHostHeaderCaseInsensitively() {
        Map<String, String> headers = new HashMap<>();
        headers.put("host", "example.jfrog.io");

        String adjustedReferer = HttpUtils.adjustRefererValue(headers, null);

        assertEquals("http://example.jfrog.io/" + HttpUtils.getArtifactoryUserAgent(), adjustedReferer);
    }

    @Test
    void getHeaderIgnoreCaseFindsMixedCaseHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Jfrog-Origin", "router");

        assertEquals("router", HttpUtils.getHeaderIgnoreCase(headers, "X-JFrog-Origin"));
    }

    @Test
    void getHeaderIgnoreCaseHandlesNullKeyInMap() {
        Map<String, String> headers = new HashMap<>();
        headers.put(null, "should-be-skipped");
        headers.put("X-Jfrog-Origin", "router");

        assertEquals("router", HttpUtils.getHeaderIgnoreCase(headers, "X-JFrog-Origin"));
    }

    @ParameterizedTest
    @CsvSource({
        // Single range
        "bytes=1000-2000, 5000, 1, 1000, 1001",
        // Prefix range
        "bytes=3000-, 5000, 1, 3000, 2000",
        // Prefix range, total size is unknown
        "bytes=3000-,, 1, 3000, -1",
        // Suffix range
        "bytes=-1000, 5000, 1, 4000, 1000",
        // Suffix range, size unknown
        "bytes=-1000,, 1, -1, 1000",
        // Multiple ranges
        "'bytes=0-500,1000-1500', 5000, 2, 0, 1002",
        // Empty / Missing Range header
        ", 5000, 0, -1, 0",
        // Invalid range
        "bytes=invalid, 5000, 0, -1, 0",
        // Not supported units
        "mb=1-1, 5000, 0, -1, 0",
        // Empty range
        "bytes=, 5000, 0, -1, 0",
    })
    void testSummarizeRangesRequested(String rangeHeader, Long totalSize,
                                      long expectedRangesCount,
                                      long expectedOffset,
                                      long expectedSize) {
        HttpUtils.RangesSummary summary = HttpUtils.summarizeRangesRequested(rangeHeader, totalSize);

        if (expectedRangesCount == 0) {
            assertNull(summary, "Expected null summary for invalid or empty range header");
        } else {
            assertNotNull(summary, "Expected non-null summary");
            assertEquals(expectedRangesCount, summary.rangesCount(), "Unexpected ranges count");
            assertEquals(expectedOffset, summary.offset(), "Unexpected offset");
            assertEquals(expectedSize, summary.size(), "Unexpected size");
        }
    }
}