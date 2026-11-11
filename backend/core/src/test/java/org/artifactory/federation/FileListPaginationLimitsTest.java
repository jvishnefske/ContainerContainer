package org.artifactory.federation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link FileListPaginationLimits}.
 */
class FileListPaginationLimitsTest {

    @Test
    void validateCreatedCursorAcceptsIso8601Utc() {
        assertDoesNotThrow(() -> FileListPaginationLimits.validateCreatedCursor("2025-01-01T00:00:00Z"));
    }

    @Test
    void validateCreatedCursorRejectsInvalidString() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FileListPaginationLimits.validateCreatedCursor("not-a-timestamp"));
        assertEquals(FileListPaginationLimits.INVALID_CREATED_PARAMETER_MESSAGE, ex.getMessage());
    }

    @Test
    void validatePageSizeAcceptsMinAndMax() {
        assertDoesNotThrow(() -> FileListPaginationLimits.validatePageSize(1));
        assertDoesNotThrow(() -> FileListPaginationLimits.validatePageSize(FileListPaginationLimits.MAX_PAGE_SIZE));
    }

    @Test
    void validatePageSizeRejectsOutOfRange() {
        IllegalArgumentException tooSmall = assertThrows(IllegalArgumentException.class,
                () -> FileListPaginationLimits.validatePageSize(0));
        assertEquals(FileListPaginationLimits.INVALID_PAGE_SIZE_MESSAGE, tooSmall.getMessage());

        IllegalArgumentException tooLarge = assertThrows(IllegalArgumentException.class,
                () -> FileListPaginationLimits.validatePageSize(FileListPaginationLimits.MAX_PAGE_SIZE + 1));
        assertEquals(FileListPaginationLimits.INVALID_PAGE_SIZE_MESSAGE, tooLarge.getMessage());
    }
}
