package org.artifactory.addon.replication;

import org.artifactory.exception.http.ContentTooLargeException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ReplicationConfigUtil}.
 *
 * @author Tomer Slutsky
 */
public class ReplicationConfigUtilTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void validateJobDetailsSize_shouldPass_whenDetailsBlobIsNull() {
        // Arrange, Act & Assert
        assertDoesNotThrow(() -> ReplicationConfigUtil.validateJobDetailsSize(null, null));
    }

    @Test
    void validateJobDetailsSize_shouldPass_whenDetailsBlobIsEmpty() {
        // Arrange, Act & Assert
        assertDoesNotThrow(() -> ReplicationConfigUtil.validateJobDetailsSize("", null));
    }

    @Test
    void validateJobDetailsSize_shouldPass_whenDetailsBlobIsSmall() {
        // Arrange
        String smallBlob = "{\"replicationMetaData\":{\"sourceRepo\":\"test-repo\"}}";

        // Act & Assert
        assertDoesNotThrow(() -> ReplicationConfigUtil.validateJobDetailsSize(smallBlob, null));
    }

    @Test
    void validateJobDetailsSize_shouldPass_whenDetailsBlobIsAtMaxAllowedSize() {
        // Arrange - Create a blob that with buffer equals exactly 2048 (maxSize)
        // maxSize = 2048, buffer = 200, so we can have 1848 characters
        String blob = "a".repeat(1848);

        // Act & Assert
        assertDoesNotThrow(() -> ReplicationConfigUtil.validateJobDetailsSize(blob, "descriptor.getReplicationKey()"));
    }

    @Test
    void validateJobDetailsSize_shouldFail_whenDetailsBlobExceedsMaxAllowedSize() {
        // Arrange - Create a blob that with buffer exceeds 2048 (maxSize)
        // maxSize = 2048, buffer = 200, so 1849 characters will exceed the limit
        String blob = "a".repeat(1849);

        // Act & Assert
        ContentTooLargeException exception = assertThrows(ContentTooLargeException.class,
                () -> ReplicationConfigUtil.validateJobDetailsSize(blob, "descriptor.getReplicationKey()"));
        assertTrue(exception.getMessage().contains("exceeds the maximum allowed size"));
        assertTrue(exception.getMessage().contains("Please reduce the number of include/exclude patterns"));
    }

    @Test
    void validateJobDetailsSize_shouldFail_whenDetailsBlobIsVeryLarge() {
        // Arrange - Create a very large blob (3000 characters, well over the 2048 limit)
        String largeBlob = "x".repeat(3000);

        // Act & Assert
        ContentTooLargeException exception = assertThrows(ContentTooLargeException.class,
                () -> ReplicationConfigUtil.validateJobDetailsSize(largeBlob, "descriptor.getReplicationKey()"));
        assertTrue(exception.getMessage().contains("exceeds the maximum allowed size"));
    }

    @Test
    void validateJobDetailsSize_shouldPass_whenDetailsBlobIsRealisticReplicationConfig() {
        // Arrange - Realistic replication configuration JSON
        String realisticBlob = """
                {
                    "replicationMetaData": {
                        "sourceRepo": "my-repository",
                        "includePathPrefixPattern": "path1/**,path2/**,path3/**",
                        "excludePathPrefixPattern": "temp/**,cache/**",
                        "targetUrl": "http://target-artifactory.example.com:8081/artifactory/target-repo",
                        "replicationStrategy": "FULL",
                        "replicationType": "PUSH"
                    }
                }
                """;

        // Act & Assert
        assertDoesNotThrow(
                () -> ReplicationConfigUtil.validateJobDetailsSize(realisticBlob, "descriptor.getReplicationKey()"));
    }

    @Test
    void validateJobDetailsSize_shouldFail_whenDetailsBlobHasLongPatterns() {
        // Arrange - Create a realistic JSON with very long patterns that exceed the limit
        String longPattern = "very/long/path/pattern/".repeat(100);
        String blobWithLongPatterns = String.format("""
                {
                    "replicationMetaData": {
                        "sourceRepo": "my-repository",
                        "includePathPrefixPattern": "%s",
                        "excludePathPrefixPattern": "",
                        "targetUrl": "http://target.com/artifactory/repo",
                        "replicationStrategy": "FULL",
                        "replicationType": "PUSH"
                    }
                }
                """, longPattern);

        // Act & Assert
        ContentTooLargeException exception = assertThrows(ContentTooLargeException.class,
                () -> ReplicationConfigUtil.validateJobDetailsSize(blobWithLongPatterns,
                        "descriptor.getReplicationKey()"));
        assertTrue(exception.getMessage().contains("exceeds the maximum allowed size"));
        assertTrue(exception.getMessage().contains("Available size for patterns and metadata"));
    }
}

