package org.artifactory.rest.resource.replication;

import jakarta.ws.rs.core.Response;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.distribution.DistributionAddon;
import org.artifactory.api.rest.distribution.bundle.models.FileSpec;
import org.artifactory.checksum.ChecksumsInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReplicationResource}.
 */
@ExtendWith(MockitoExtension.class)
class ReplicationResourceTest {

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private DistributionAddon distributionAddon;
    @Mock
    private ChecksumsInfo checksums;

    private ReplicationResource replicationResource;

    @BeforeEach
    void setUp() {
        replicationResource = new ReplicationResource();
        ReflectionTestUtils.setField(replicationResource, "addonsManager", addonsManager);
        when(addonsManager.addonByType(DistributionAddon.class)).thenReturn(distributionAddon);
    }

    @Test
    void replicateArtifactStreaming_shouldPopulateProperties_whenIncludePropertiesIsTrue() {
        // Arrange
        FileSpec fileSpec = createValidFileSpec();
        when(distributionAddon.validateFileAndGetChecksums(fileSpec)).thenReturn(checksums);

        // Act
        Response response = replicationResource.replicateArtifactStreaming(
                fileSpec,
                "file-tx-id",
                "delegate-token",
                "Bearer auth-token",
                "tx/path/123",
                true);

        // Assert
        assertEquals(200, response.getStatus());
        verify(distributionAddon).populateArtifactProperties(fileSpec);
        verify(distributionAddon).validateFileAndGetChecksums(fileSpec);
    }

    @Test
    void replicateArtifactStreaming_shouldNotPopulateProperties_whenIncludePropertiesIsFalse() {
        // Arrange
        FileSpec fileSpec = createValidFileSpec();
        when(distributionAddon.validateFileAndGetChecksums(fileSpec)).thenReturn(checksums);

        // Act
        Response response = replicationResource.replicateArtifactStreaming(
                fileSpec,
                "file-tx-id",
                "delegate-token",
                "Bearer auth-token",
                "tx/path/123",
                false);

        // Assert
        assertEquals(200, response.getStatus());
        verify(distributionAddon, never()).populateArtifactProperties(any());
        verify(distributionAddon).validateFileAndGetChecksums(fileSpec);
    }

    private FileSpec createValidFileSpec() {
        FileSpec fileSpec = new FileSpec();
        fileSpec.setSourcePath("source-repo/path/to/artifact.jar");
        fileSpec.setTargetPath("target-repo/path/to/artifact.jar");
        return fileSpec;
    }
}
