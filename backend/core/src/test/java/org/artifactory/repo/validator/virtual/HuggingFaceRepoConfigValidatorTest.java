package org.artifactory.repo.validator.virtual;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.model.VirtualRepositoryRepoTypeConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;

import static org.artifactory.util.PackageTypeConfigFields.HUGGINGFACE_ML_REPO_LAYOUT;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Einas Ashkar
 */
class HuggingFaceRepoConfigValidatorTest {
    @Mock
    private RepositoryService repositoryService;
    private AutoCloseable mocks;
    HuggingFaceRepoConfigValidator huggingFaceRepoConfigValidator;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        huggingFaceRepoConfigValidator = new HuggingFaceRepoConfigValidator(repositoryService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void ValidHuggingFaceRepoConfigWhenVirtualConfigIsNullTest() {
        VirtualRepoConfig virtualRepoConfig = null;
        huggingFaceRepoConfigValidator.validate(virtualRepoConfig);
        verify(repositoryService, never()).getRepoConfigByKey(anyString());
    }

    @Test
    void ValidHuggingFaceRepoConfigWhenRepositoryConfigServiceIsNullTest() {
        VirtualRepoConfig virtualRepoConfig = Mockito.mock(VirtualRepoConfig.class);
        HuggingFaceRepoConfigValidator huggingFaceRepoConfigValidator =
                new HuggingFaceRepoConfigValidator(null);
        huggingFaceRepoConfigValidator.validate(virtualRepoConfig);
        verify(repositoryService, never()).getRepoConfigByKey(anyString());
    }

    @Test
    void NonHuggingFaceRepoTest() {
        VirtualRepoConfig repoConfig = Mockito.mock(VirtualRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);
        assertDoesNotThrow(() -> huggingFaceRepoConfigValidator.validate(repoConfig));
        verify(repositoryService, never()).getRepoConfigByKey(anyString());
    }

    @Test
    void HuggingFaceVirtualRepoWithMixedLayouts_throwsExceptionTest() {
        VirtualRepoConfig virtualRepoConfig = createVirtualHuggingFaceRepoWithTwoLocals(true, false);
        org.junit.jupiter.api.Assertions.assertThrows(
                org.artifactory.exception.InvalidOperationException.class,
                () -> huggingFaceRepoConfigValidator.validate(virtualRepoConfig)
        );
    }

    @Test
    void HuggingFaceVirtualRepoWithAllMLLayoutsTrue_doesNotThrowTest() {
        VirtualRepoConfig virtualRepoConfig = createVirtualHuggingFaceRepoWithTwoLocals(true, true);
        assertDoesNotThrow(() -> huggingFaceRepoConfigValidator.validate(virtualRepoConfig));
    }

    @Test
    void GetRepoTypeTest() {
        assertEquals(RepoType.HuggingFaceML, huggingFaceRepoConfigValidator.getRepoType());
    }

    private VirtualRepoConfig createVirtualHuggingFaceRepoWithTwoLocals(boolean firstHasMLLayout,
            boolean secondHasMLLayout) {
        RepoConfig repo1 = Mockito.mock(RepoConfig.class);
        RepoConfig repo2 = Mockito.mock(RepoConfig.class);
        HashMap<String, String> config1 = new java.util.HashMap<>();
        config1.put(HUGGINGFACE_ML_REPO_LAYOUT.getKey(), Boolean.toString(firstHasMLLayout));
        HashMap<String, String> config2 = new java.util.HashMap<>();
        config2.put(HUGGINGFACE_ML_REPO_LAYOUT.getKey(), Boolean.toString(secondHasMLLayout));
        Mockito.when(repo1.getPackageTypeConfig()).thenReturn(config1);
        Mockito.when(repo2.getPackageTypeConfig()).thenReturn(config2);
        List<String> repoRefs = java.util.List.of("repo1", "repo2");
        Mockito.when(repositoryService.getRepoConfigByKey("repo1")).thenReturn(repo1);
        Mockito.when(repositoryService.getRepoConfigByKey("repo2")).thenReturn(repo2);
        VirtualRepoConfig virtualRepoConfig = Mockito.mock(VirtualRepoConfig.class);
        Mockito.when(virtualRepoConfig.getPackageType()).thenReturn(RepoType.HuggingFaceML);
        Mockito.when(virtualRepoConfig.getKey()).thenReturn("virtual-hf");
        VirtualRepositoryRepoTypeConfig virtualRepoTypeConfig = Mockito.mock(VirtualRepositoryRepoTypeConfig.class);
        Mockito.when(virtualRepoConfig.getRepoTypeConfig()).thenReturn(virtualRepoTypeConfig);
        Mockito.when(virtualRepoTypeConfig.getRepositoryRefs()).thenReturn(repoRefs);
        return virtualRepoConfig;
    }
}