package org.artifactory.repo.service;

import org.artifactory.api.crypto.CryptoService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.sensitive.fields.CryptoServiceImpl;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CryptoService} as implemented by {@link CryptoServiceImpl}.
 * Uses {@link LocalRepoConfig} so the graph has no sensitive fields (empty handler list is valid).
 *
 * @author Fawaz Atwa
 */
class CryptoServiceTest {

    private CryptoService service;

    @BeforeEach
    void setUp() {
        ArtifactoryHome home = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties props = mock(ArtifactorySystemProperties.class);
        File keyFile = mock(File.class);
        when(props.getLongProperty(ConstantValues.sensitiveRepoConfigMaxRecursionDepth)).thenReturn(20L);
        when(home.getArtifactoryProperties()).thenReturn(props);
        when(home.getArtifactoryKey()).thenReturn(keyFile);
        when(keyFile.exists()).thenReturn(false);
        ArtifactoryHome.bind(home);
        service = new CryptoServiceImpl(List.of());
    }

    @AfterEach
    void tearDown() {
        ArtifactoryHome.unbind();
    }

    @Test
    void decryptReturnsNewListAndDoesNotMutateInput() {
        LocalRepoConfig config = LocalRepoConfig.create("repo", RepoType.Generic);
        config.getRepoTypeConfig().setMaxUniqueSnapshots(7);
        Collection<RepoConfig> input = List.<RepoConfig>of(config);

        List<RepoConfig> result = service.decrypt(input);

        assertEquals(1, result.size());
        assertNotSame(config, result.get(0));
        assertEquals("repo", result.get(0).getKey());
        assertEquals(7, ((LocalRepoConfig) result.get(0)).getRepoTypeConfig().getMaxUniqueSnapshots());
        assertEquals(7, config.getRepoTypeConfig().getMaxUniqueSnapshots());
    }

    @Test
    void maskReturnsNewListAndDoesNotMutateInput() {
        LocalRepoConfig config = LocalRepoConfig.create("repo", RepoType.Generic);
        config.getRepoTypeConfig().setMaxUniqueSnapshots(3);
        Collection<RepoConfig> input = List.<RepoConfig>of(config);

        List<RepoConfig> result = service.mask(input);

        assertEquals(1, result.size());
        assertNotSame(config, result.get(0));
        assertEquals("repo", result.get(0).getKey());
        assertEquals(3, config.getRepoTypeConfig().getMaxUniqueSnapshots());
    }

    @Test
    void reEncryptThrowsWhenEncryptionKeyNotAvailable() {
        LocalRepoConfig config = LocalRepoConfig.create("repo", RepoType.Generic);
        Collection<RepoConfig> input = List.<RepoConfig>of(config);
        EncryptionWrapper wrapper = mock(EncryptionWrapper.class);

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> service.reEncrypt(input, wrapper));

        assertTrue(thrown.getMessage().contains("Cannot reEncrypt"));
        assertTrue(thrown.getMessage().contains("encryption key not available"));
    }

    @Test
    void reEncryptThrowsWhenEncryptionWrapperNull() {
        LocalRepoConfig config = LocalRepoConfig.create("repo", RepoType.Generic);
        Collection<RepoConfig> input = List.<RepoConfig>of(config);

        assertThrows(NullPointerException.class, () -> service.reEncrypt(input, null));
    }

    @Test
    void decryptWithEmptyCollectionReturnsEmptyList() {
        List<RepoConfig> result = service.decrypt(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void decryptWithNullReturnsEmptyList() {
        List<RepoConfig> result = service.decrypt(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void decryptPreservesNullEntriesInCollection() {
        LocalRepoConfig config = LocalRepoConfig.create("repo", RepoType.Generic);
        List<RepoConfig> input = new java.util.ArrayList<>();
        input.add(config);
        input.add(null);
        input.add(LocalRepoConfig.create("other", RepoType.Generic));

        List<RepoConfig> result = service.decrypt(input);

        assertEquals(3, result.size());
        assertNotSame(config, result.get(0));
        assertNull(result.get(1));
        assertEquals("repo", result.get(0).getKey());
        assertEquals("other", result.get(2).getKey());
    }
}
