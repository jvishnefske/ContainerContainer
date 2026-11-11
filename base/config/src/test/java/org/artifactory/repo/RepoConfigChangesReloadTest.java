package org.artifactory.repo;

import com.google.common.collect.Lists;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoConfigChangesReloadTest {

    private RepoConfigChangesReload repoConfigChangesReload;
    private RepoConfigChangeReload repoConfigReload1;
    private RepoConfigChangeReload repoConfigReload2;

    @BeforeEach
    void setUp() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repo1", Generic);
        RemoteRepoConfig newRemoteRepoConfig = RemoteRepoConfig.create("repo2", Generic);
        newRemoteRepoConfig.setModification(456L);
        RemoteRepoConfig oldRemoteRepoConfig = RemoteRepoConfig.create("repo2", Generic);
        RepoConfigChange repoConfigChange1 = new RepoConfigChange("repo1", REPO_CREATE, null, localRepoConfig);
        RepoConfigChange repoConfigChange2 =
                new RepoConfigChange("repo2", REPO_UPDATE, newRemoteRepoConfig, oldRemoteRepoConfig);
        repoConfigReload1 = new RepoConfigChangeReload(repoConfigChange1);
        repoConfigReload2 = new RepoConfigChangeReload(repoConfigChange2);
        Collection<RepoConfigChangeReload> reloads = Lists.newArrayList(repoConfigReload1, repoConfigReload2);
        repoConfigChangesReload = new RepoConfigChangesReload(reloads);
    }

    @Test
    void testFilterByRepoType() {
        List<RepoConfigChangeReload> localRepos = repoConfigChangesReload.filterBy(LOCAL);
        assertEquals(1, localRepos.size());
        assertEquals(repoConfigReload1, localRepos.getFirst());
    }

    @Test
    void testFilterByRepoTypeAndAction() {
        List<RepoConfigChangeReload> remoteUpdateRepos = repoConfigChangesReload.filterBy(REMOTE, REPO_UPDATE);
        assertEquals(1, remoteUpdateRepos.size());
        assertEquals(repoConfigReload2, remoteUpdateRepos.getFirst());
    }

    @Test
    void testFilterByRepoTypesAndAction() {
        Set<RepoDetailsType> repoTypes = Set.of(LOCAL, REMOTE);
        List<RepoConfigChangeReload> repos = repoConfigChangesReload.filterBy(repoTypes, REPO_UPDATE);
        assertEquals(1, repos.size());
        assertEquals(repoConfigReload2, repos.getFirst());
    }

    @Test
    void testGetAll() {
        List<RepoConfigChangeReload> allRepos = repoConfigChangesReload.getAll();
        assertEquals(2, allRepos.size());
        assertTrue(allRepos.contains(repoConfigReload1));
        assertTrue(allRepos.contains(repoConfigReload2));
    }

    @Test
    void testAdd() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("repo3", Generic);
        RepoConfigChange repoConfigChange3 = new RepoConfigChange("repo3", REPO_CREATE, virtualRepoConfig, null);
        RepoConfigChangeReload repoConfigReload3 = new RepoConfigChangeReload(repoConfigChange3);
        repoConfigChangesReload.add(repoConfigReload3);
        List<RepoConfigChangeReload> allRepos = repoConfigChangesReload.getAll();
        assertEquals(3, allRepos.size());
        assertTrue(allRepos.contains(repoConfigReload3));
    }

    @Test
    void testFromChanges() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repo1", Generic);
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repo2", Generic);
        RepoConfigChange repoConfigChange1 = RepoConfigChange.createOf(localRepoConfig);
        RepoConfigChange repoConfigChange2 = RepoConfigChange.updateOf(remoteRepoConfig, remoteRepoConfig);
        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChangesByType = Map.of(
                LOCAL, List.of(repoConfigChange1),
                REMOTE, List.of(repoConfigChange2)
        );

        RepoConfigChangesReload result = RepoConfigChangesReload.fromChanges(repoConfigChangesByType);

        assertEquals(2, result.getAll().size());
        assertEquals(1, result.filterBy(LOCAL).size());
        assertEquals(1, result.filterBy(REMOTE).size());
        assertEquals(localRepoConfig.getKey(), result.filterBy(LOCAL).getFirst().getRepoKey());
        assertEquals(remoteRepoConfig.getKey(), result.filterBy(REMOTE).getFirst().getRepoKey());
    }

    @Test
    void testToRepoConfigReloadMap() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repo1", Generic);
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repo2", Generic);
        RepoConfigChange repoConfigChange1 = RepoConfigChange.createOf(localRepoConfig);
        RepoConfigChange repoConfigChange2 = RepoConfigChange.updateOf(remoteRepoConfig, remoteRepoConfig);
        RepoConfigChangeReload repoConfigReload1 = new RepoConfigChangeReload(repoConfigChange1);
        RepoConfigChangeReload repoConfigReload2 = new RepoConfigChangeReload(repoConfigChange2);
        Collection<RepoConfigChangeReload> reloads = Lists.newArrayList(repoConfigReload1, repoConfigReload2);
        RepoConfigChangesReload repoConfigChangesReload = new RepoConfigChangesReload(reloads);

        Map<RepoDetailsType, List<RepoConfigReload>> result = repoConfigChangesReload.toRepoConfigReloadMap();

        assertEquals(2, result.size());
        assertTrue(result.containsKey(LOCAL));
        assertTrue(result.containsKey(REMOTE));
        assertEquals(1, result.get(LOCAL).size());
        assertEquals(1, result.get(REMOTE).size());
        assertEquals(localRepoConfig.getKey(), result.get(LOCAL).getFirst().getRepoKey());
        assertEquals(remoteRepoConfig.getKey(), result.get(REMOTE).getFirst().getRepoKey());
    }
}