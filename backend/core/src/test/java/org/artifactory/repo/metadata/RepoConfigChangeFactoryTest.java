package org.artifactory.repo.metadata;

import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoMetadata;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepoConfigChangeFactoryTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private RepoMetadataResolver metadataResolver;

    private RepoConfigChangeFactory underTest;

    @BeforeEach
    void setUp() {
        getOrCreateArtifactoryHomeStub();
        underTest = new RepoConfigChangeFactory(metadataResolver);
    }

    @Test
    void create_shouldAttachResolvedMetadata_whenRepoCudEventsEnabled() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudWorkerEventsEnabled, "true");
        LocalRepoConfig repoConfig = LocalRepoConfig.create("libs-release", RepoType.Generic);
        RepoMetadata metadata = new RepoMetadata("proj", true, false, List.of("DEV"));
        when(metadataResolver.resolve(repoConfig)).thenReturn(metadata);

        RepoConfigChange change = underTest.create(repoConfig);

        assertSame(metadata, change.getRepoMetadata());
        verify(metadataResolver).resolve(repoConfig);
    }

    @Test
    void update_shouldAttachResolvedMetadata_whenOnlyJfbusFlagEnabled() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudJfbusEventsEnabled, "true");
        LocalRepoConfig oldConfig = LocalRepoConfig.create("libs-release", RepoType.Generic);
        LocalRepoConfig newConfig = LocalRepoConfig.create("libs-release", RepoType.Generic);
        RepoMetadata metadata = RepoMetadata.empty();
        when(metadataResolver.resolve(newConfig)).thenReturn(metadata);

        RepoConfigChange change = underTest.update(oldConfig, newConfig);

        assertSame(metadata, change.getRepoMetadata());
    }

    @Test
    void delete_shouldSkipMetadataResolution_whenBothFlagsDisabled() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudWorkerEventsEnabled, "false");
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudJfbusEventsEnabled, "false");
        LocalRepoConfig repoConfig = LocalRepoConfig.create("libs-release", RepoType.Generic);

        RepoConfigChange change = underTest.delete(repoConfig);

        assertNull(change.getRepoMetadata());
        verifyNoInteractions(metadataResolver);
    }

    @Test
    void enrich_shouldReturnNull_whenChangeIsNull() {
        assertNull(underTest.enrich(null));
    }

    @Test
    void enrich_shouldUseOldRepoConfig_whenNewConfigIsNull() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudWorkerEventsEnabled, "true");
        LocalRepoConfig repoConfig = LocalRepoConfig.create("libs-release", RepoType.Generic);
        RepoConfigChange change = RepoConfigChange.deleteOf(repoConfig);
        RepoMetadata metadata = RepoMetadata.empty();
        when(metadataResolver.resolve(repoConfig)).thenReturn(metadata);

        RepoConfigChange enriched = underTest.enrich(change);

        assertEquals(change, enriched);
        assertSame(metadata, enriched.getRepoMetadata());
    }
}
