package org.artifactory.repo.service;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoConfigRootFolderKeyExtractorTest {

    private static final String CACHE_SUFFIX = LocalCacheRepoConfig.PATH_SUFFIX;

    @Test
    void extractRootFolderKeys_shouldReturnRepoKey_whenLocalRepo() {
        // Arrange
        LocalRepoConfig localRepo = LocalRepoConfig.create("my-local-repo", RepoType.Generic);

        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeys(Set.of(localRepo));

        // Assert
        assertEquals(Set.of("my-local-repo"), result);
    }

    @Test
    void extractRootFolderKeys_shouldReturnCacheKey_whenRemoteRepoWithCachingEnabled() {
        // Arrange
        RemoteRepoConfig remoteRepo = RemoteRepoConfig.create("my-remote-repo", RepoType.Generic);
        remoteRepo.getRepoTypeConfig().setStoreArtifactsLocally(true);

        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeys(Set.of(remoteRepo));

        // Assert
        assertEquals(Set.of("my-remote-repo" + CACHE_SUFFIX), result);
    }

    @Test
    void extractRootFolderKeys_shouldReturnEmptySet_whenRemoteRepoWithCachingDisabled() {
        // Arrange
        RemoteRepoConfig remoteRepo = RemoteRepoConfig.create("my-remote-repo", RepoType.Generic);
        remoteRepo.getRepoTypeConfig().setStoreArtifactsLocally(false);

        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeys(Set.of(remoteRepo));

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void extractRootFolderKeys_shouldReturnAllKeys_whenMixedRepoTypes() {
        // Arrange
        LocalRepoConfig localRepo = LocalRepoConfig.create("local-repo", RepoType.Generic);
        RemoteRepoConfig remoteWithCache = RemoteRepoConfig.create("remote-with-cache", RepoType.Generic);
        remoteWithCache.getRepoTypeConfig().setStoreArtifactsLocally(true);
        RemoteRepoConfig remoteNoCache = RemoteRepoConfig.create("remote-no-cache", RepoType.Generic);
        remoteNoCache.getRepoTypeConfig().setStoreArtifactsLocally(false);

        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeys(
                Set.of(localRepo, remoteWithCache, remoteNoCache));

        // Assert
        assertEquals(Set.of("local-repo", "remote-with-cache" + CACHE_SUFFIX), result);
    }

    @Test
    void extractRootFolderKeys_shouldReturnEmptySet_whenEmptyInput() {
        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeys(Set.of());

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void extractRootFolderKeysForInsert_shouldReturnKeys_whenRepoCreateAction() {
        // Arrange
        LocalRepoConfig localRepo = LocalRepoConfig.create("new-local", RepoType.Generic);
        RepoConfigChange createChange = new RepoConfigChange("new-local", REPO_CREATE, localRepo, null);
        Map<RepoDetailsType, List<RepoConfigChange>> changesByType = new HashMap<>();
        changesByType.put(RepoDetailsType.LOCAL, List.of(createChange));

        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForInsert(
                new RepoConfigChanges(changesByType));

        // Assert
        assertEquals(Set.of("new-local"), result);
    }

    @Test
    void extractRootFolderKeysForInsert_shouldIgnore_whenRepoUpdateAction() {
        // Arrange
        LocalRepoConfig localRepo = LocalRepoConfig.create("updated-local", RepoType.Generic);
        RepoConfigChange updateChange = new RepoConfigChange("updated-local", REPO_UPDATE, localRepo, localRepo);
        Map<RepoDetailsType, List<RepoConfigChange>> changesByType = new HashMap<>();
        changesByType.put(RepoDetailsType.LOCAL, List.of(updateChange));

        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForInsert(
                new RepoConfigChanges(changesByType));

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void extractRootFolderKeysForInsert_shouldIgnore_whenRepoDeleteAction() {
        // Arrange
        LocalRepoConfig localRepo = LocalRepoConfig.create("deleted-local", RepoType.Generic);
        RepoConfigChange deleteChange = new RepoConfigChange("deleted-local", REPO_DELETE, null, localRepo);
        Map<RepoDetailsType, List<RepoConfigChange>> changesByType = new HashMap<>();
        changesByType.put(RepoDetailsType.LOCAL, List.of(deleteChange));

        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForInsert(
                new RepoConfigChanges(changesByType));

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void extractRootFolderKeysForInsert_shouldIgnore_whenNewConfigIsNull() {
        // Arrange - REPO_CREATE with null newConfig (edge case)
        RepoConfigChange createWithNullConfig = new RepoConfigChange("null-config", REPO_CREATE, null, null);
        Map<RepoDetailsType, List<RepoConfigChange>> changesByType = new HashMap<>();
        changesByType.put(RepoDetailsType.LOCAL, List.of(createWithNullConfig));

        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForInsert(
                new RepoConfigChanges(changesByType));

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void extractRootFolderKeysForInsert_shouldReturnCacheKey_whenRemoteRepoCreatedWithCaching() {
        // Arrange
        RemoteRepoConfig remoteRepo = RemoteRepoConfig.create("new-remote", RepoType.Generic);
        remoteRepo.getRepoTypeConfig().setStoreArtifactsLocally(true);
        RepoConfigChange createChange = new RepoConfigChange("new-remote", REPO_CREATE, remoteRepo, null);
        Map<RepoDetailsType, List<RepoConfigChange>> changesByType = new HashMap<>();
        changesByType.put(RepoDetailsType.REMOTE, List.of(createChange));

        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForInsert(
                new RepoConfigChanges(changesByType));

        // Assert
        assertEquals(Set.of("new-remote" + CACHE_SUFFIX), result);
    }

    @Test
    void extractRootFolderKeysForInsert_shouldReturnEmptySet_whenEmptyChanges() {
        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForInsert(
                new RepoConfigChanges(Map.of()));

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void extractRootFolderKeysForUpdate_shouldReturnCacheKey_whenCachingJustEnabled() {
        // Arrange - oldConfig had caching disabled, newConfig has caching enabled
        RemoteRepoConfig oldConfig = RemoteRepoConfig.create("my-remote", RepoType.Generic);
        oldConfig.getRepoTypeConfig().setStoreArtifactsLocally(false);
        RemoteRepoConfig newConfig = RemoteRepoConfig.create("my-remote", RepoType.Generic);
        newConfig.getRepoTypeConfig().setStoreArtifactsLocally(true);

        RepoConfigChange updateChange = new RepoConfigChange("my-remote", REPO_UPDATE, newConfig, oldConfig);
        Map<RepoDetailsType, List<RepoConfigChange>> changesByType = new HashMap<>();
        changesByType.put(RepoDetailsType.REMOTE, List.of(updateChange));

        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForUpdate(
                new RepoConfigChanges(changesByType));

        // Assert
        assertEquals(Set.of("my-remote" + CACHE_SUFFIX), result);
    }

    @Test
    void extractRootFolderKeysForUpdate_shouldReturnCacheKey_whenOldConfigIsNull() {
        // Arrange - oldConfig is null (edge case), newConfig has caching enabled
        RemoteRepoConfig newConfig = RemoteRepoConfig.create("my-remote", RepoType.Generic);
        newConfig.getRepoTypeConfig().setStoreArtifactsLocally(true);

        RepoConfigChange updateChange = new RepoConfigChange("my-remote", REPO_UPDATE, newConfig, null);
        Map<RepoDetailsType, List<RepoConfigChange>> changesByType = new HashMap<>();
        changesByType.put(RepoDetailsType.REMOTE, List.of(updateChange));

        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForUpdate(
                new RepoConfigChanges(changesByType));

        // Assert
        assertEquals(Set.of("my-remote" + CACHE_SUFFIX), result);
    }

    @Test
    void extractRootFolderKeysForUpdate_shouldReturnEmptySet_whenCachingWasAlreadyEnabled() {
        // Arrange - both oldConfig and newConfig have caching enabled (no change)
        RemoteRepoConfig oldConfig = RemoteRepoConfig.create("my-remote", RepoType.Generic);
        oldConfig.getRepoTypeConfig().setStoreArtifactsLocally(true);
        RemoteRepoConfig newConfig = RemoteRepoConfig.create("my-remote", RepoType.Generic);
        newConfig.getRepoTypeConfig().setStoreArtifactsLocally(true);

        RepoConfigChange updateChange = new RepoConfigChange("my-remote", REPO_UPDATE, newConfig, oldConfig);
        Map<RepoDetailsType, List<RepoConfigChange>> changesByType = new HashMap<>();
        changesByType.put(RepoDetailsType.REMOTE, List.of(updateChange));

        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForUpdate(
                new RepoConfigChanges(changesByType));

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void extractRootFolderKeysForUpdate_shouldReturnEmptySet_whenCachingDisabled() {
        // Arrange - newConfig has caching disabled
        RemoteRepoConfig oldConfig = RemoteRepoConfig.create("my-remote", RepoType.Generic);
        oldConfig.getRepoTypeConfig().setStoreArtifactsLocally(true);
        RemoteRepoConfig newConfig = RemoteRepoConfig.create("my-remote", RepoType.Generic);
        newConfig.getRepoTypeConfig().setStoreArtifactsLocally(false);

        RepoConfigChange updateChange = new RepoConfigChange("my-remote", REPO_UPDATE, newConfig, oldConfig);
        Map<RepoDetailsType, List<RepoConfigChange>> changesByType = new HashMap<>();
        changesByType.put(RepoDetailsType.REMOTE, List.of(updateChange));

        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForUpdate(
                new RepoConfigChanges(changesByType));

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void extractRootFolderKeysForUpdate_shouldIgnore_whenNotRemoteRepo() {
        // Arrange - local repo update should be ignored
        LocalRepoConfig oldConfig = LocalRepoConfig.create("my-local", RepoType.Generic);
        LocalRepoConfig newConfig = LocalRepoConfig.create("my-local", RepoType.Generic);

        RepoConfigChange updateChange = new RepoConfigChange("my-local", REPO_UPDATE, newConfig, oldConfig);
        Map<RepoDetailsType, List<RepoConfigChange>> changesByType = new HashMap<>();
        changesByType.put(RepoDetailsType.LOCAL, List.of(updateChange));

        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForUpdate(
                new RepoConfigChanges(changesByType));

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void extractRootFolderKeysForUpdate_shouldIgnore_whenNotUpdateAction() {
        // Arrange - REPO_CREATE action should be ignored
        RemoteRepoConfig newConfig = RemoteRepoConfig.create("my-remote", RepoType.Generic);
        newConfig.getRepoTypeConfig().setStoreArtifactsLocally(true);

        RepoConfigChange createChange = new RepoConfigChange("my-remote", REPO_CREATE, newConfig, null);
        Map<RepoDetailsType, List<RepoConfigChange>> changesByType = new HashMap<>();
        changesByType.put(RepoDetailsType.REMOTE, List.of(createChange));

        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForUpdate(
                new RepoConfigChanges(changesByType));

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void extractRootFolderKeysForUpdate_shouldReturnEmptySet_whenEmptyChanges() {
        // Act
        Set<String> result = RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForUpdate(
                new RepoConfigChanges(Map.of()));

        // Assert
        assertTrue(result.isEmpty());
    }
}
