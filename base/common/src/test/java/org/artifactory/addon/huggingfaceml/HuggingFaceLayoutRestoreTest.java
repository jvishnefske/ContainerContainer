package org.artifactory.addon.huggingfaceml;

import lombok.SneakyThrows;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author shahafg
 */
class HuggingFaceLayoutRestoreTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private SecurityService securityService;
    @Captor
    private ArgumentCaptor<RepoConfig> repoConfigCaptor;

    private AutoCloseable mocks;
    private HuggingFaceLayoutRestore restore;

    @SneakyThrows
    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        restore = spy(new HuggingFaceLayoutRestore(repositoryService, securityService, false));
        when(securityService.callAsSystem(any())).thenAnswer(invocation -> {
            Callable<String> callable = invocation.getArgument(0);
            return callable.call();
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void restorePaths(boolean dryRun) {
        String repoKey = "validRepo";
        final RepoConfig validRepoConfig = createValidRepoConfig(repoKey);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(validRepoConfig);
        List<String> paths = List.of(
                ".jfrog/old-layout/models/name/revision/2024-09-12T13:25:52.272Z/.jfrog_huggingface_model_info.json",
                ".jfrog/old-layout/datasets/name/revision/.latest_huggingface_dataset_info.json"
        );
        restore = spy(new HuggingFaceLayoutRestore(repositoryService,
                securityService, dryRun));
        when(repositoryService.exists(
                eq(RepoPathFactory.create(repoKey + "-cache", ".jfrog/old-layout/models/")))).thenReturn(
                true);
        when(repositoryService.exists(
                eq(RepoPathFactory.create(repoKey + "-cache", ".jfrog/old-layout/datasets")))).thenReturn(
                true);

        when(repositoryService.exists(
                eq(RepoPathFactory.create(repoKey + "-cache", "models")))).thenReturn(
                true);
        when(repositoryService.exists(
                eq(RepoPathFactory.create(repoKey + "-cache", "datasets")))).thenReturn(
                true);

        restore.restore(repoKey);
        // two bad paths should be skipped

        verify(repositoryService, times(paths.size())).copyToCache(any(), any(), eq(dryRun), eq(false), eq(false),
                eq(false), eq(null), eq(false));
        // Without properties
        if (!dryRun) {
            verify(repositoryService, times(paths.size())).undeploy(any());
            verify(repositoryService, times(1)).updateRepoConfigWithoutChecks(repoConfigCaptor.capture());
            assertEquals("false", repoConfigCaptor.getValue().getPackageTypeConfig().get(IS_ML_REPO_LAYOUT));
        } else {
            verify(repositoryService, never()).undeploy(any());
            verify(repositoryService, never()).updateRepoConfigWithoutChecks(any());
        }
    }

    @SneakyThrows
    @Test
    void getHuggingFaceFilesByRepoFromDB_withInvalidRepo_doesNotRestorePaths() {
        String repoKey = "invalidRepo";
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(null);

        restore.restore(repoKey);

        verify(repositoryService, never()).moveFlat(any(), anyString(), anyString(), any(), any(), anyBoolean(),
                anyBoolean(), anyInt());
        verify(restore, never()).pullBackupForRepoContent(anyString(), any());
        verify(repositoryService, never()).updateRepoConfigWithoutChecks(any());
    }

    @Test
    void isValidRepoForLayoutConvert_withValidRepo_returnsNull() {
        String repoKey = "validRepo";
        final RepoConfig validRepoConfig = createValidRepoConfig(repoKey);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(validRepoConfig);

        assertNull(restore.validateRepoForLayoutRestore(repoKey));
    }

    @Test
    void isValidReturnsFalseForAnAbsentRepo() {
        String repoKey = "invalidRepo";
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(null);

        final String result = restore.validateRepoForLayoutRestore(repoKey);
        assertEquals("Repository invalidRepo descriptor not found", result);
    }

    @Test
    void isValidReturnsFalseForNonHFRepo() {
        String repoKey = "nonHFRepo";
        final RepoConfig nonHFRepoConfig = createValidRepoConfig(repoKey, RepoType.NuGet, false);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(nonHFRepoConfig);

        final String result = restore.validateRepoForLayoutRestore(repoKey);
        assertEquals("Repository nonHFRepo is not HuggingFaceML type", result);
    }

    @Test
    void isValidReturnsFalseForHFRepoWithMLRepoLayout() {
        String repoKey = "hfRepo";
        final RepoConfig huggingFaceRepoConfig = createValidRepoConfig(repoKey, RepoType.HuggingFaceML, false);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(huggingFaceRepoConfig);

        final String result = restore.validateRepoForLayoutRestore(repoKey);
        assertEquals("Repository hfRepo is already using old layout", result);
    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(strings = {"repoWithChildren", "repoWithoutChildren"})
    void createBackupForRepoContentForLocal(String repoKeyForConversion) {
        RepoPath sourcePath = RepoPathFactory.create(repoKeyForConversion);
        RepoPath backupPath =
                RepoPathFactory.create(repoKeyForConversion, HuggingFaceLayoutConverter.BACKUP_DIR + "/models");
        when(repositoryService.exists(eq(backupPath))).thenReturn(true);
        restore.pullBackupForRepoContent(repoKeyForConversion, RepoDetailsType.LOCAL);

        if ("repoWithChildren".equals(repoKeyForConversion)) {
            verify(repositoryService, times(1)).copy(eq(backupPath), eq(sourcePath), anyBoolean(), anyBoolean(),
                    anyBoolean(),
                    anyBoolean(), any(), anyBoolean());
        } else {
            verify(repositoryService, never()).copy(any(), any());
            verify(repositoryService, never()).copyToCache(eq(backupPath), eq(sourcePath), anyBoolean(), anyBoolean(),
                    anyBoolean(),
                    anyBoolean(), any(), anyBoolean());
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(strings = {"local-repo", "remote-repo-cache"})
    void pullBackupForRepoContentForRepos(String repoKeyForConversion) {
        RepoPath sourcePath = RepoPathFactory.create(repoKeyForConversion);
        RepoPath backupPath =
                RepoPathFactory.create(repoKeyForConversion, HuggingFaceLayoutConverter.BACKUP_DIR + "/models");
        when(repositoryService.exists(
                eq(RepoPathFactory.create(repoKeyForConversion, ".jfrog/old-layout/models")))).thenReturn(true);
        when(repositoryService.exists(
                eq(RepoPathFactory.create(repoKeyForConversion, ".jfrog/old-layout/datasets")))).thenReturn(true);
        if ("remote-repo-cache".equals(repoKeyForConversion)) {
            restore.pullBackupForRepoContent(repoKeyForConversion, RepoDetailsType.REMOTE);
            verify(repositoryService, times(1)).copyToCache(eq(backupPath), eq(sourcePath), anyBoolean(), eq(false),
                    eq(false), eq(false), eq(null), eq(false));
        } else {
            restore.pullBackupForRepoContent(repoKeyForConversion, RepoDetailsType.LOCAL);
            verify(repositoryService, times(1)).copy(eq(backupPath), eq(sourcePath), anyBoolean(), eq(false), eq(true),
                    eq(false), eq(null), eq(false));
        }
    }

    private RepoConfig createValidRepoConfig(String repoKey) {
        return createValidRepoConfig(repoKey, RepoType.HuggingFaceML, true);
    }

    private RepoConfig createValidRepoConfig(String repoKey, RepoType repoType, boolean isMlRepoLayout) {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(repoConfig.getPackageType()).thenReturn(repoType);
        Map<String, String> packageTypeConfig = new HashMap<>();
        packageTypeConfig.put(IS_ML_REPO_LAYOUT, String.valueOf(isMlRepoLayout));
        when(repoConfig.getPackageTypeConfig()).thenReturn(packageTypeConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.REMOTE);
        return repoConfig;
    }
}