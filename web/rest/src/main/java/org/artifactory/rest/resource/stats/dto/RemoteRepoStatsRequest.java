package org.artifactory.rest.resource.stats.dto;

import org.artifactory.common.ConstantValues;
import org.artifactory.remote.stats.RemoteRepoStatsSortFields;
import org.artifactory.remote.stats.StatusPatternMatcher;
import org.artifactory.exception.http.BadRequestException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Request DTO for the remote repo stats query API.
 * Supports both GET (query params) and POST (form-encoded) via the {@link #from} factory.
 *
 * @author Hussein Alshqerat
 */
public record RemoteRepoStatsRequest(
        String startDate,
        String endDate,
        List<String> repoKeys,
        List<String> packageTypes,
        Float minErrorRate,
        String statusPattern,
        boolean includeStatusBreakdown,
        boolean includeDailyBreakdown,
        String sortBy,
        String order,
        int offset,
        int limit
) {

    private static final Set<String> VALID_SORT_FIELDS =
            RemoteRepoStatsSortFields.ALL;

    public static RemoteRepoStatsRequest from(
            String startDate, String endDate,
            String repoKeys, String packageTypes,
            Float minErrorRate, String statusPattern,
            boolean includeStatusBreakdown, boolean includeDailyBreakdown,
            String sortBy, String order, int offset, int limit) {
        return new RemoteRepoStatsRequest(
                startDate != null ? startDate : Instant.now().minus(24, ChronoUnit.HOURS).toString(),
                endDate != null ? endDate : Instant.now().toString(),
                repoKeys != null && !repoKeys.isBlank()
                        ? List.of(repoKeys.split(",")).stream().map(String::trim).toList()
                        : List.of(),
                packageTypes != null && !packageTypes.isBlank()
                        ? List.of(packageTypes.split(",")).stream().map(String::trim).toList()
                        : List.of(),
                minErrorRate,
                statusPattern,
                includeStatusBreakdown,
                includeDailyBreakdown,
                sortBy != null && !sortBy.isBlank() ? sortBy : RemoteRepoStatsSortFields.TOTAL_REQUESTS,
                order != null && !order.isBlank() ? order : "desc",
                offset,
                limit > 0 ? limit : 50
        );
    }

    /**
     * Validates all request parameters. Throws {@link BadRequestException}
     * on the first validation failure (mapped to HTTP 400).
     */
    public void validate() {
        Instant start = parseInstant(startDate, "startDate");
        Instant end = parseInstant(endDate, "endDate");
        if (start.isAfter(end)) {
            throw new BadRequestException("startDate must be before endDate");
        }
        int maxFilterSize = ConstantValues.remoteRepoStatsMaxFilterSize.getInt();
        if (repoKeys.size() > maxFilterSize) {
            throw new BadRequestException(
                    "repoKeys exceeds maximum of " + maxFilterSize);
        }
        if (packageTypes.size() > maxFilterSize) {
            throw new BadRequestException(
                    "packageTypes exceeds maximum of " + maxFilterSize);
        }
        if (minErrorRate != null && (minErrorRate < 0.0f || minErrorRate > 1.0f)) {
            throw new BadRequestException("minErrorRate must be between 0.0 and 1.0");
        }
        if (!VALID_SORT_FIELDS.contains(sortBy)) {
            throw new BadRequestException(
                    "Invalid sortBy value: " + sortBy + ". Valid values: " + VALID_SORT_FIELDS);
        }
        if (!"asc".equalsIgnoreCase(order) && !"desc".equalsIgnoreCase(order)) {
            throw new BadRequestException("order must be 'asc' or 'desc'");
        }
        if (limit < 1 || limit > 100) {
            throw new BadRequestException("limit must be between 1 and 100");
        }
        if (offset < 0) {
            throw new BadRequestException("offset must be >= 0");
        }
        if (statusPattern != null && !statusPattern.isBlank()) {
            try {
                StatusPatternMatcher.compile(statusPattern);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            }
        }
    }

    /**
     * Converts this REST DTO into the domain request object used by the service layer.
     */
    public org.artifactory.remote.stats.RemoteRepoStatsRequest toDomainRequest() {
        return new org.artifactory.remote.stats.RemoteRepoStatsRequest(
                startDate, endDate, repoKeys, packageTypes,
                minErrorRate, statusPattern,
                includeStatusBreakdown, includeDailyBreakdown,
                sortBy, order, offset, limit);
    }

    private static Instant parseInstant(String value, String paramName) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(
                    "Invalid " + paramName + " format. Expected ISO 8601 (e.g. 2026-02-22T00:00:00Z)");
        }
    }
}
