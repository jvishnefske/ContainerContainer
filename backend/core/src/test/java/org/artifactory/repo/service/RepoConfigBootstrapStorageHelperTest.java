package org.artifactory.repo.service;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.storage.db.repo.service.RepositoryConfigStorageService;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapperImpl;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.testng.Assert;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Tamar Rosen
 */
class RepoConfigBootstrapStorageHelperTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private InternalArtifactoryContext context;
    private AutoCloseable autoCloseable;

    @BeforeEach
    public void beforeEach() throws InterruptedException {
        autoCloseable = openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void afterEach() throws Exception {
        autoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void bulkInsertRepositoryConfigs() {
        RepositoryConfigStorageService storageService = mock(RepositoryConfigStorageService.class);
        RepoConfigBootstrapStorageHelper repoConfigBootstrapStorageHelper =
                new RepoConfigBootstrapStorageHelper(storageService,
                        Set.of(new DummyInterceptor()), null, null);
        // given
        LocalRepoConfig badRepoConfig = LocalRepoConfig.create("bad-config-repo", RepoType.Generic);
        badRepoConfig.getBaseConfig().setDescription("bad-config");

        // when (upgrade scenario)
        try {
            repoConfigBootstrapStorageHelper.bulkInsertRepositoryConfigs(Set.of(badRepoConfig), true);
        } catch (ConfigurationException e) {
            Assert.fail(
                    "Repo config interceptors should not throw an exception in an upgrade scenario. Please make sure " +
                            "no 'REPO_CREATE' changes are passed to the interceptors during an upgrade.");
        }

        // then
        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        verify(storageService).bulkInsertRepositoryConfigs(captor.capture(), any());
        Set<DbRepositoryConfig> insertedRepos = captor.getValue();
        assertEquals(2, insertedRepos.size());
        Map<String, DbRepositoryConfig> insertedReposMap =
                insertedRepos.stream().collect(Collectors.toMap(DbRepositoryConfig::getKey, Function.identity()));
        assertTrue(insertedReposMap.containsKey("bad-config-repo"));
        assertTrue(insertedReposMap.containsKey("created-repo"));
        RepositoryConfigMapperImpl repositoryConfigMapper = new RepositoryConfigMapperImpl();
        RepoConfig insertedBadRepoConfig =
                repositoryConfigMapper.dbInfoBlobToRepositoryConfig(insertedReposMap.get("bad-config-repo"));
        assertNull(insertedBadRepoConfig.getBaseConfig().getDescription());
    }

    @Test
    void bulkInsertRepositoryConfigs_shouldPassRootFolderKeys_whenAtomicCreationEnabledUpgrade() {
        // Arrange
        getBound().setProperty(ConstantValues.repoRootAtomicCreation, "true");
        RepositoryConfigStorageService storageService = mock(RepositoryConfigStorageService.class);
        RepoConfigBootstrapStorageHelper helper = new RepoConfigBootstrapStorageHelper(
                storageService, Set.of(), null, null);

        LocalRepoConfig localRepo = LocalRepoConfig.create("local-repo", RepoType.Generic);

        // Act - upgrade scenario
        helper.bulkInsertRepositoryConfigs(Set.of(localRepo), true);

        // Assert - root folder keys should be extracted and passed
        ArgumentCaptor<Set<String>> rootFolderCaptor = ArgumentCaptor.forClass(Set.class);
        verify(storageService).bulkInsertRepositoryConfigs(any(), rootFolderCaptor.capture());
        Set<String> rootFolderKeys = rootFolderCaptor.getValue();
        assertTrue(rootFolderKeys.contains("local-repo"));
    }

    @Test
    void bulkInsertRepositoryConfigs_shouldPassRootFolderKeys_whenAtomicCreationEnabledNonUpgrade() {
        // Arrange
        getBound().setProperty(ConstantValues.repoRootAtomicCreation, "true");
        RepositoryConfigStorageService storageService = mock(RepositoryConfigStorageService.class);

        // Create a RepoConfigChangesMapFunction that returns a REPO_CREATE change
        RepoConfigChangesMapFunction changesMapFunction = configs -> {
            LocalRepoConfig config = configs.iterator().next() instanceof LocalRepoConfig
                    ? (LocalRepoConfig) configs.iterator().next()
                    : LocalRepoConfig.create("test", RepoType.Generic);
            return Map.of(RepoDetailsType.LOCAL, List.of(
                    new RepoConfigChange(config.getKey(), REPO_CREATE, config, null)));
        };

        RepoConfigBootstrapStorageHelper helper = new RepoConfigBootstrapStorageHelper(
                storageService, Set.of(), null, changesMapFunction);

        LocalRepoConfig localRepo = LocalRepoConfig.create("new-local-repo", RepoType.Generic);

        // Act - non-upgrade scenario
        helper.bulkInsertRepositoryConfigs(Set.of(localRepo), false);

        // Assert - root folder keys should be extracted and passed
        ArgumentCaptor<Set<String>> rootFolderCaptor = ArgumentCaptor.forClass(Set.class);
        verify(storageService).bulkInsertRepositoryConfigs(any(), rootFolderCaptor.capture());
        Set<String> rootFolderKeys = rootFolderCaptor.getValue();
        assertTrue(rootFolderKeys.contains("new-local-repo"));
    }

    @Test
    void bulkUpdateRepositoryConfigs_shouldPassRootFolderKeys_whenAtomicCreationEnabled() {
        // Arrange
        getBound().setProperty(ConstantValues.repoRootAtomicCreation, "true");
        RepositoryConfigStorageService storageService = mock(RepositoryConfigStorageService.class);

        // Create a RepoConfigChangesMapFunction that returns a REPO_UPDATE change
        RepoConfigChangesMapFunction changesMapFunction = configs -> {
            LocalRepoConfig config = configs.iterator().next() instanceof LocalRepoConfig
                    ? (LocalRepoConfig) configs.iterator().next()
                    : LocalRepoConfig.create("test", RepoType.Generic);
            return Map.of(RepoDetailsType.LOCAL, List.of(
                    new RepoConfigChange(config.getKey(), REPO_UPDATE, config, config)));
        };

        RepoConfigBootstrapStorageHelper helper = new RepoConfigBootstrapStorageHelper(
                storageService, Set.of(), null, changesMapFunction);

        LocalRepoConfig localRepo = LocalRepoConfig.create("update-repo", RepoType.Generic);

        // Act
        helper.bulkUpdateRepositoryConfigs(Set.of(localRepo));

        // Assert - root folder keys should be empty for local repo update (no cache)
        ArgumentCaptor<Set<String>> rootFolderCaptor = ArgumentCaptor.forClass(Set.class);
        verify(storageService).bulkUpdateRepositoryConfigs(any(), rootFolderCaptor.capture());
        Set<String> rootFolderKeys = rootFolderCaptor.getValue();
        // Local repo updates don't need root folders created (they should already exist)
        assertTrue(rootFolderKeys.isEmpty());
    }

    @Test
    void bulkInsertRepositoryConfigs_shouldPassEmptyRootFolderKeys_whenAtomicCreationDisabled() {
        // Arrange
        getBound().setProperty(ConstantValues.repoRootAtomicCreation, "false");
        RepositoryConfigStorageService storageService = mock(RepositoryConfigStorageService.class);
        RepoConfigBootstrapStorageHelper helper = new RepoConfigBootstrapStorageHelper(
                storageService, Set.of(), null, null);

        LocalRepoConfig localRepo = LocalRepoConfig.create("local-repo", RepoType.Generic);

        // Act - upgrade scenario
        helper.bulkInsertRepositoryConfigs(Set.of(localRepo), true);

        // Assert - root folder keys should be empty when feature is disabled
        verify(storageService).bulkInsertRepositoryConfigs(any(), eq(Set.of()));
    }

    @Test
    void bulkUpdateRepositoryConfigs_shouldPassEmptyRootFolderKeys_whenAtomicCreationDisabled() {
        // Arrange
        getBound().setProperty(ConstantValues.repoRootAtomicCreation, "false");
        RepositoryConfigStorageService storageService = mock(RepositoryConfigStorageService.class);

        RepoConfigChangesMapFunction changesMapFunction = configs -> {
            LocalRepoConfig config = configs.iterator().next() instanceof LocalRepoConfig
                    ? (LocalRepoConfig) configs.iterator().next()
                    : LocalRepoConfig.create("test", RepoType.Generic);
            return Map.of(RepoDetailsType.LOCAL, List.of(
                    new RepoConfigChange(config.getKey(), REPO_UPDATE, config, config)));
        };

        RepoConfigBootstrapStorageHelper helper = new RepoConfigBootstrapStorageHelper(
                storageService, Set.of(), null, changesMapFunction);

        LocalRepoConfig localRepo = LocalRepoConfig.create("update-repo", RepoType.Generic);

        // Act
        helper.bulkUpdateRepositoryConfigs(Set.of(localRepo));

        // Assert - root folder keys should be empty when feature is disabled
        verify(storageService).bulkUpdateRepositoryConfigs(any(), eq(Set.of()));
    }

    static class DummyInterceptor implements RepoConfigChangeInterceptor {
        @Override
        public void onBeforeSave(Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {
            List<RepoConfigChange> localChanges = mutableRepoConfigChanges.get(RepoDetailsType.LOCAL);
            // 1. misconfiguration: create -> throw exception, update -> mutate and continue
            localChanges.stream()
                    .filter(change -> change.getNewRepoConfig() != null)
                    .filter(change -> "bad-config".equals(change.getNewRepoConfig().getBaseConfig().getDescription()))
                    .forEach(change -> {
                        if (REPO_CREATE == change.getConfigAction()) {
                            throw new ConfigurationException("");
                        }
                        if (REPO_UPDATE == change.getConfigAction()) {
                            change.getNewRepoConfig().getBaseConfig().setDescription(null);
                        }
                    });

            // 2. create default repo
            RepoConfigChange createChange = new RepoConfigChange("created-repo", REPO_CREATE,
                    LocalRepoConfig.create("created-repo", RepoType.Generic), null);
            localChanges.add(createChange);
            // 3. delete repo
            RepoConfigChange deleteChange =
                    new RepoConfigChange("deleted-repo", RepoConfigChange.ConfigAction.REPO_DELETE, null,
                            LocalRepoConfig.create("deleted-repo", RepoType.Generic));
            localChanges.add(deleteChange);
        }
    }
}
