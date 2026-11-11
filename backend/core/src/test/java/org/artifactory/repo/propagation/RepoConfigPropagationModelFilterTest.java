package org.artifactory.repo.propagation;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.BasicRepoConfig;
import org.artifactory.model.RepoConfigPropagationModel;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoConfigReload;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.storage.db.repo.service.RepositoryConfigStorageService;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

@Test
public class RepoConfigPropagationModelFilterTest {

    @Test
    public void testFilterCreateRepoActionsWhenDbIsEmpty() {
        // given
        RepositoryConfigStorageService storageService = mock(RepositoryConfigStorageService.class);
        when(storageService.findKeysFromSet(anySet())).thenReturn(Set.of("artifactory-build-info"));

        RepoConfigPropagationModel model = new RepoConfigPropagationModel();
        model.setRepoConfigCreateOrUpdate(Set.of(
                createRepoConfigReload("repo1", 1L, RepoConfigChange.ConfigAction.REPO_CREATE),
                createRepoConfigReload("repo2", 1L, RepoConfigChange.ConfigAction.REPO_UPDATE)));
        model.setRepoConfigDelete(Set.of(
                createRepoConfigReload("repo3", 1L, RepoConfigChange.ConfigAction.REPO_CREATE)));

        // when
        RepoConfigPropagationModelFilter modelFilter =
                new RepoConfigPropagationModelFilter(Map.of(), storageService, 10);
        modelFilter.filter(model);

        // then
        assertTrue(model.getRepoConfigCreateOrUpdate().isEmpty());
        assertEquals(model.getRepoConfigDelete().size(), 1);
        assertTrue(toRepoKeys(model.getRepoConfigDelete()).contains("repo3"));
    }

    @Test(dataProvider = "keysCount")
    public void testFilterCreateRepoActions(int keysCount) {
        // given
        RepositoryConfigStorageService storageService = mock(RepositoryConfigStorageService.class);
        if (keysCount == 1) {
            when(storageService.getAllRepoKeys()).thenReturn(Set.of("repo1", "repo2", "repo3"));
        } else {
            when(storageService.findKeysFromSet(anySet())).thenReturn(Set.of("repo1", "repo2", "repo3"));
        }

        Map<String, BasicRepoConfig> cacheData = new HashMap<>();
        cacheData.put("repo1", new BasicRepoConfig("repo1", 1L, RepoDetailsType.LOCAL, RepoType.Bower));
        cacheData.put("repo2", new BasicRepoConfig("repo2", 1L, RepoDetailsType.LOCAL, RepoType.Bower));

        Set<RepoConfigReload> reposToCreate = new HashSet<>();
        // the same as in the cache. Should be skipped
        reposToCreate.add(createRepoConfigReload("repo1", 1L, RepoConfigChange.ConfigAction.REPO_CREATE));
        // modification date is changed. Should be processed
        reposToCreate.add(createRepoConfigReload("repo2", 2L, RepoConfigChange.ConfigAction.REPO_CREATE));
        // does not exist in the cache. Should be processed
        reposToCreate.add(createRepoConfigReload("repo3", 0L, RepoConfigChange.ConfigAction.REPO_CREATE));
        // does not exist in DB. Should be skipped
        reposToCreate.add(createRepoConfigReload("repo4", 0L, RepoConfigChange.ConfigAction.REPO_CREATE));
        RepoConfigPropagationModel model = new RepoConfigPropagationModel();
        model.setRepoConfigCreateOrUpdate(reposToCreate);

        // when
        RepoConfigPropagationModelFilter modelFilter =
                new RepoConfigPropagationModelFilter(cacheData, storageService, keysCount);
        modelFilter.filter(model);

        // then
        assertTrue(model.getRepoConfigDelete().isEmpty());

        Set<String> repoCreateKeys = toRepoKeys(model.getRepoConfigCreateOrUpdate());
        assertEquals(2, repoCreateKeys.size());
        assertTrue(repoCreateKeys.contains("repo2"));
        assertTrue(repoCreateKeys.contains("repo3"));

        if (keysCount == 1) {
            verify(storageService).getAllRepoKeys();
        } else {
            verify(storageService).findKeysFromSet(anySet());
        }
    }

    @Test(dataProvider = "keysCount")
    public void testFilterUpdateRepoActions(int keysCount) {
        // given
        RepositoryConfigStorageService storageService = mock(RepositoryConfigStorageService.class);
        if (keysCount == 1) {
            when(storageService.getAllRepoKeys()).thenReturn(Set.of("repo1", "repo2", "repo3"));
        } else {
            when(storageService.findKeysFromSet(anySet())).thenReturn(Set.of("repo1", "repo2", "repo3"));
        }

        Map<String, BasicRepoConfig> cacheData = new HashMap<>();
        cacheData.put("repo1", new BasicRepoConfig("repo1", 1L, RepoDetailsType.LOCAL, RepoType.Bower));
        cacheData.put("repo2", new BasicRepoConfig("repo2", 1L, RepoDetailsType.LOCAL, RepoType.Bower));

        Set<RepoConfigReload> reposToUpdate = new HashSet<>();
        // the same as in the cache. Should be skipped
        reposToUpdate.add(createRepoConfigReload("repo1", 1L, RepoConfigChange.ConfigAction.REPO_UPDATE));
        // modification date is changed. Should be processed
        reposToUpdate.add(createRepoConfigReload("repo2", 2L, RepoConfigChange.ConfigAction.REPO_UPDATE));
        // does not exist in the cache. Should be processed
        reposToUpdate.add(createRepoConfigReload("repo3", 0L, RepoConfigChange.ConfigAction.REPO_UPDATE));
        // does not exist in DB. Should be skipped
        reposToUpdate.add(createRepoConfigReload("repo4", 0L, RepoConfigChange.ConfigAction.REPO_UPDATE));
        RepoConfigPropagationModel model = new RepoConfigPropagationModel();
        model.setRepoConfigCreateOrUpdate(reposToUpdate);

        // when
        RepoConfigPropagationModelFilter filter =
                new RepoConfigPropagationModelFilter(cacheData, storageService, keysCount);
        filter.filter(model);

        // then
        assertTrue(model.getRepoConfigDelete().isEmpty());

        Set<String> repoCreateKeys = toRepoKeys(model.getRepoConfigCreateOrUpdate());
        assertEquals(2, repoCreateKeys.size());
        assertTrue(repoCreateKeys.contains("repo2"));
        assertTrue(repoCreateKeys.contains("repo3"));

        if (keysCount == 1) {
            verify(storageService).getAllRepoKeys();
        } else {
            verify(storageService).findKeysFromSet(anySet());
        }
    }

    @Test(dataProvider = "keysCount")
    public void testFilterDeleteRepoActions(int keysCount) {
        // given
        RepositoryConfigStorageService storageService = mock(RepositoryConfigStorageService.class);
        if (keysCount == 1) {
            when(storageService.getAllRepoKeys()).thenReturn(Set.of("repo1", "repo2"));
        } else {
            when(storageService.findKeysFromSet(anySet())).thenReturn(Set.of("repo1", "repo2"));
        }

        Map<String, BasicRepoConfig> cacheData = new HashMap<>();
        cacheData.put("repo1", new BasicRepoConfig("repo1", 1L, RepoDetailsType.LOCAL, RepoType.Bower));
        cacheData.put("repo2", new BasicRepoConfig("repo2", 1L, RepoDetailsType.LOCAL, RepoType.Bower));
        cacheData.put("repo4", new BasicRepoConfig("repo4", 0L, RepoDetailsType.LOCAL, RepoType.Bower));
        cacheData.put("repo5", new BasicRepoConfig("repo5", 0L, RepoDetailsType.LOCAL, RepoType.Bower));

        Set<RepoConfigReload> reposToUpdate = new HashSet<>();
        // the same as in the cache. Present in the DB. Should be skipped
        reposToUpdate.add(createRepoConfigReload("repo1", 1L, RepoConfigChange.ConfigAction.REPO_DELETE));
        // modification date is changed. Present in the DB. Should be skipped since
        reposToUpdate.add(createRepoConfigReload("repo2", 2L, RepoConfigChange.ConfigAction.REPO_DELETE));
        // does not exist in the cache. Should be skipped
        reposToUpdate.add(createRepoConfigReload("repo3", 0L, RepoConfigChange.ConfigAction.REPO_DELETE));
        // present in the cache and does not exist in DB. Should be processed
        reposToUpdate.add(createRepoConfigReload("repo4", 0L, RepoConfigChange.ConfigAction.REPO_DELETE));
        // present in the cache/ Modification date is different. Does not exist in DB. Should be processed
        reposToUpdate.add(createRepoConfigReload("repo5", 1L, RepoConfigChange.ConfigAction.REPO_DELETE));
        RepoConfigPropagationModel model = new RepoConfigPropagationModel();
        model.setRepoConfigDelete(reposToUpdate);

        // when
        RepoConfigPropagationModelFilter filter =
                new RepoConfigPropagationModelFilter(cacheData, storageService, keysCount);
        filter.filter(model);

        // then
        assertTrue(model.getRepoConfigCreateOrUpdate().isEmpty());

        Set<String> repoCreateKeys = toRepoKeys(model.getRepoConfigDelete());
        assertEquals(2, repoCreateKeys.size());
        assertTrue(repoCreateKeys.contains("repo4"));
        assertTrue(repoCreateKeys.contains("repo5"));

        if (keysCount == 1) {
            verify(storageService).getAllRepoKeys();
        } else {
            verify(storageService).findKeysFromSet(anySet());
        }
    }

    @Test(dataProvider = "keysCount")
    public void testDbQueryCounts(int keysCount) {
        // given
        RepositoryConfigStorageService storageService = mock(RepositoryConfigStorageService.class);
        if (keysCount == 1) {
            when(storageService.getAllRepoKeys()).thenReturn(Set.of("repo1", "repo2"));
        } else {
            when(storageService.findKeysFromSet(anySet())).thenReturn(Set.of("repo1", "repo2"));
        }

        Map<String, BasicRepoConfig> cacheData = new HashMap<>();
        cacheData.put("repo2", new BasicRepoConfig("repo2", 1L, RepoDetailsType.LOCAL, RepoType.Bower));
        cacheData.put("repo3", new BasicRepoConfig("repo2", 1L, RepoDetailsType.LOCAL, RepoType.Bower));

        Set<RepoConfigReload> reposToCreateUpdate = new HashSet<>();
        Set<RepoConfigReload> reposToDelete = new HashSet<>();
        reposToCreateUpdate.add(createRepoConfigReload("repo1", 1L, RepoConfigChange.ConfigAction.REPO_CREATE));
        reposToCreateUpdate.add(createRepoConfigReload("repo2", 2L, RepoConfigChange.ConfigAction.REPO_UPDATE));
        reposToDelete.add(createRepoConfigReload("repo3", 1L, RepoConfigChange.ConfigAction.REPO_DELETE));


        RepoConfigPropagationModel model = new RepoConfigPropagationModel();
        model.setRepoConfigCreateOrUpdate(reposToCreateUpdate);
        model.setRepoConfigDelete(reposToDelete);

        // when
        RepoConfigPropagationModelFilter modelFilter =
                new RepoConfigPropagationModelFilter(cacheData, storageService, keysCount);
        modelFilter.filter(model);

        // then
        assertEquals(model.getRepoConfigDelete().size(), 1);
        assertTrue(toRepoKeys(model.getRepoConfigDelete()).contains("repo3"));

        Set<String> repoCreateUpdateKeys = toRepoKeys(model.getRepoConfigCreateOrUpdate());
        assertEquals(2, repoCreateUpdateKeys.size());
        assertTrue(repoCreateUpdateKeys.contains("repo1"));
        assertTrue(repoCreateUpdateKeys.contains("repo2"));

        if (keysCount == 1) {
            verify(storageService).getAllRepoKeys();
        } else {
            verify(storageService, times(2)).findKeysFromSet(anySet());
        }
    }

    @DataProvider
    public Object[][] keysCount() {
        return new Object[][] {
                {1},
                {500}
        };
    }

    private static RepoConfigReload createRepoConfigReload(String repoKey, Long modification,
            RepoConfigChange.ConfigAction action) {
        return new RepoConfigReload(repoKey, RepoDetailsType.LOCAL, RepoType.Go,
                action, modification);
    }

    private static Set<String> toRepoKeys(Set<RepoConfigReload> configs) {
        return configs.stream()
                .map(RepoConfigReload::getRepoKey)
                .collect(Collectors.toSet());
    }
}