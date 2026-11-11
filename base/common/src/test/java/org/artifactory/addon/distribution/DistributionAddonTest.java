package org.artifactory.addon.distribution;

import org.artifactory.api.rest.distribution.bundle.models.FileSpec;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.util.UnsupportedByLicenseException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link DistributionAddon} interface default methods.
 */
class DistributionAddonTest {

    private final DistributionAddon addon = () -> true;

    @Test
    void populateArtifactProperties_shouldThrowException_whenCalledOnDefaultImplementation() {
        // Arrange
        FileSpec fileSpec = new FileSpec();

        // Act & Assert
        assertThrows(UnsupportedByLicenseException.class,
                () -> addon.populateArtifactProperties(fileSpec),
                "Default implementation should throw UnsupportedByLicenseException");
    }

    @Test
    void distributeArtifactStreaming_shouldThrowException_whenCalledOnDefaultImplementation() {
        // Arrange
        FileSpec fileSpec = new FileSpec();
        ChecksumsInfo checksums = mock(ChecksumsInfo.class);
        OutputStream outputStream = new ByteArrayOutputStream();

        // Act & Assert
        assertThrows(UnsupportedByLicenseException.class,
                () -> addon.distributeArtifactStreaming(
                        "file-tx-id", fileSpec, "delegate-token", "auth", checksums, outputStream),
                "Default implementation should throw UnsupportedByLicenseException");
    }

    @Test
    void validateFileAndGetChecksums_shouldThrowException_whenCalledOnDefaultImplementation() {
        // Arrange
        FileSpec fileSpec = new FileSpec();

        // Act & Assert
        assertThrows(UnsupportedByLicenseException.class,
                () -> addon.validateFileAndGetChecksums(fileSpec),
                "Default implementation should throw UnsupportedByLicenseException");
    }

    @Test
    void distributeArtifact_shouldThrowException_whenCalledOnDefaultImplementation() {
        // Arrange
        FileSpec fileSpec = new FileSpec();

        // Act & Assert
        assertThrows(UnsupportedByLicenseException.class,
                () -> addon.distributeArtifact(fileSpec, "delegate-token", "auth"),
                "Default implementation should throw UnsupportedByLicenseException");
    }
}
