package org.artifactory.federation;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Limits and validation messages for the federated file list paged API.
 */
public final class FileListPaginationLimits {

    private FileListPaginationLimits() {}

    public static final int MAX_PAGE_SIZE = 200_000;

    public static final String INVALID_PAGE_SIZE_MESSAGE =
            "Invalid \"pageSize\" parameter: must be between 1 and " + MAX_PAGE_SIZE + " inclusive.";

    public static final String INVALID_CREATED_PARAMETER_MESSAGE =
            "Invalid \"created\" parameter: must be a valid ISO-8601 date-time (parsed relative to UTC).";

    public static void validateCreatedCursor(String createdCursor) {
        try {
            Instant.parse(createdCursor);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(INVALID_CREATED_PARAMETER_MESSAGE);
        }
    }

    public static void validatePageSize(int pageSize) {
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(INVALID_PAGE_SIZE_MESSAGE);
        }
    }

}