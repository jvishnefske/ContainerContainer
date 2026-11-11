package org.artifactory.rest.resource.stats.dto;

import org.artifactory.exception.http.BadRequestException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RemoteRepoStatsRequest}.
 *
 * @author Hussein Alshqerat
 */
class RemoteRepoStatsRequestTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String START = "2026-02-01T00:00:00Z";
    private static final String END = "2026-02-22T00:00:00Z";

    // ---- from() factory ----

    @Test
    void from_shouldApplyDefaults_whenNullsProvided() {
        RemoteRepoStatsRequest req = RemoteRepoStatsRequest.from(
                null, null, null, null, null, null,
                false, false, null, null, 0, 0);

        assertNotNull(req.startDate());
        assertNotNull(req.endDate());
        assertEquals(List.of(), req.repoKeys());
        assertEquals(List.of(), req.packageTypes());
        assertNull(req.minErrorRate());
        assertNull(req.statusPattern());
        assertEquals("totalRequests", req.sortBy());
        assertEquals("desc", req.order());
        assertEquals(0, req.offset());
        assertEquals(50, req.limit());
    }

    @Test
    void from_shouldParseCommaSeparatedRepoKeys_whenProvided() {
        RemoteRepoStatsRequest req = RemoteRepoStatsRequest.from(
                START, END, "repo-a,repo-b", null, null, null,
                false, false, null, null, 0, 50);

        assertEquals(List.of("repo-a", "repo-b"), req.repoKeys());
    }

    @Test
    void from_shouldTrimCommaSeparatedRepoKeys_whenSpacesPresent() {
        RemoteRepoStatsRequest req = RemoteRepoStatsRequest.from(
                START, END, "repo-a , repo-b , repo-c", null, null, null,
                false, false, null, null, 0, 50);

        assertEquals(List.of("repo-a", "repo-b", "repo-c"), req.repoKeys());
    }

    @Test
    void from_shouldParseCommaSeparatedPackageTypes_whenProvided() {
        RemoteRepoStatsRequest req = RemoteRepoStatsRequest.from(
                START, END, null, "maven,npm", null, null,
                false, false, null, null, 0, 50);

        assertEquals(List.of("maven", "npm"), req.packageTypes());
    }

    @Test
    void from_shouldReturnEmptyList_whenBlankRepoKeys() {
        RemoteRepoStatsRequest req = RemoteRepoStatsRequest.from(
                START, END, "  ", null, null, null,
                false, false, null, null, 0, 50);

        assertTrue(req.repoKeys().isEmpty());
    }

    @Test
    void from_shouldPreserveExplicitValues_whenAllProvided() {
        RemoteRepoStatsRequest req = RemoteRepoStatsRequest.from(
                START, END, "repo-a", "maven",
                0.5f, "4xx", true, true,
                "errorRate", "asc", 10, 25);

        assertEquals(START, req.startDate());
        assertEquals(END, req.endDate());
        assertEquals(List.of("repo-a"), req.repoKeys());
        assertEquals(List.of("maven"), req.packageTypes());
        assertEquals(0.5f, req.minErrorRate());
        assertEquals("4xx", req.statusPattern());
        assertTrue(req.includeStatusBreakdown());
        assertTrue(req.includeDailyBreakdown());
        assertEquals("errorRate", req.sortBy());
        assertEquals("asc", req.order());
        assertEquals(10, req.offset());
        assertEquals(25, req.limit());
    }

    // ---- validate() ----

    @Test
    void validate_shouldPass_whenValidRequest() {
        RemoteRepoStatsRequest req = RemoteRepoStatsRequest.from(
                START, END, null, null, null, null,
                false, false, null, null, 0, 50);

        assertDoesNotThrow(req::validate);
    }

    @Test
    void validate_shouldThrow_whenStartDateAfterEndDate() {
        RemoteRepoStatsRequest req = RemoteRepoStatsRequest.from(
                END, START, null, null, null, null,
                false, false, null, null, 0, 50);

        assertThrows(BadRequestException.class, req::validate);
    }

    @Test
    void validate_shouldThrow_whenInvalidDateFormat() {
        RemoteRepoStatsRequest req = new RemoteRepoStatsRequest(
                "not-a-date", END, List.of(), List.of(),
                null, null, false, false,
                "totalRequests", "desc", 0, 50);

        assertThrows(BadRequestException.class, req::validate);
    }

    @Test
    void validate_shouldThrow_whenMinErrorRateAboveOne() {
        RemoteRepoStatsRequest req = RemoteRepoStatsRequest.from(
                START, END, null, null, 1.5f, null,
                false, false, null, null, 0, 50);

        assertThrows(BadRequestException.class, req::validate);
    }

    @Test
    void validate_shouldThrow_whenMinErrorRateBelowZero() {
        RemoteRepoStatsRequest req = RemoteRepoStatsRequest.from(
                START, END, null, null, -0.1f, null,
                false, false, null, null, 0, 50);

        assertThrows(BadRequestException.class, req::validate);
    }

    @Test
    void validate_shouldThrow_whenInvalidSortBy() {
        RemoteRepoStatsRequest req = new RemoteRepoStatsRequest(
                START, END, List.of(), List.of(),
                null, null, false, false,
                "invalidField", "desc", 0, 50);

        assertThrows(BadRequestException.class, req::validate);
    }

    @Test
    void validate_shouldThrow_whenInvalidOrder() {
        RemoteRepoStatsRequest req = new RemoteRepoStatsRequest(
                START, END, List.of(), List.of(),
                null, null, false, false,
                "totalRequests", "sideways", 0, 50);

        assertThrows(BadRequestException.class, req::validate);
    }

    @Test
    void validate_shouldThrow_whenLimitExceeds100() {
        RemoteRepoStatsRequest req = new RemoteRepoStatsRequest(
                START, END, List.of(), List.of(),
                null, null, false, false,
                "totalRequests", "desc", 0, 200);

        assertThrows(BadRequestException.class, req::validate);
    }

    @Test
    void validate_shouldThrow_whenLimitIsZero() {
        RemoteRepoStatsRequest req = new RemoteRepoStatsRequest(
                START, END, List.of(), List.of(),
                null, null, false, false,
                "totalRequests", "desc", 0, 0);

        assertThrows(BadRequestException.class, req::validate);
    }

    @Test
    void validate_shouldThrow_whenNegativeOffset() {
        RemoteRepoStatsRequest req = new RemoteRepoStatsRequest(
                START, END, List.of(), List.of(),
                null, null, false, false,
                "totalRequests", "desc", -1, 50);

        assertThrows(BadRequestException.class, req::validate);
    }

    @Test
    void validate_shouldThrow_whenInvalidStatusPattern() {
        RemoteRepoStatsRequest req = RemoteRepoStatsRequest.from(
                START, END, null, null, null, "[invalid",
                false, false, null, null, 0, 50);

        assertThrows(BadRequestException.class, req::validate);
    }

    @Test
    void validate_shouldPass_whenValidStatusPattern() {
        RemoteRepoStatsRequest req = RemoteRepoStatsRequest.from(
                START, END, null, null, null, "4xx,5xx",
                false, false, null, null, 0, 50);

        assertDoesNotThrow(req::validate);
    }

    @Test
    void validate_shouldAcceptAllValidSortFields() {
        for (String field : List.of("totalRequests", "errorRate",
                "avgResponseTime", "avgTtfb", "totalResponseSize")) {
            RemoteRepoStatsRequest req = new RemoteRepoStatsRequest(
                    START, END, List.of(), List.of(),
                    null, null, false, false,
                    field, "asc", 0, 50);
            assertDoesNotThrow(req::validate,
                    "validate() should pass for sortBy=" + field);
        }
    }
}
