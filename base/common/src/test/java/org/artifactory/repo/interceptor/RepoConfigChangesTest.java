package org.artifactory.repo.interceptor;

import com.google.common.collect.Lists;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.descriptor.repo.RepoType.Maven;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoConfigChangesTest {

    private RepoConfigChanges repoConfigChanges;
    private RepoConfigChange repoConfigChange1;
    private RepoConfigChange repoConfigChange2;

    @BeforeEach
    public void setUp() {
        String repoKey1 = "repo1";
        String repoKey2 = "repo2";
        LocalRepoConfig localRepoConfig1 = LocalRepoConfig.create(repoKey1, Generic);
        LocalRepoConfig localRepoConfig2 = LocalRepoConfig.create(repoKey2, Generic);
        repoConfigChange1 = RepoConfigChange.createOf(localRepoConfig1);
        repoConfigChange2 = RepoConfigChange.createOf(localRepoConfig2);
        Map<RepoDetailsType, List<RepoConfigChange>> immutableRepoConfigChangesMap = Map.of(
                LOCAL, Lists.newArrayList(repoConfigChange1),
                FEDERATED, Lists.newArrayList(repoConfigChange2)
        );
        repoConfigChanges = RepoConfigChanges.of(new HashMap<>(immutableRepoConfigChangesMap));
    }

    @Test
    void testFilterByAction() {
        List<RepoConfigChange> changes = repoConfigChanges.filterBy(REPO_CREATE);
        assertEquals(2, changes.size());
        assertTrue(changes.contains(repoConfigChange1));
        assertTrue(changes.contains(repoConfigChange2));
    }

    @Test
    void testFilterByAction_NoChanges() {
        RepoConfigChanges repoConfigChanges = new RepoConfigChanges(Map.of());
        List<RepoConfigChange> changes = repoConfigChanges.filterBy(REPO_CREATE);
        assertTrue(changes.isEmpty());
    }

    @Test
    void testFilterByRepoType() {
        List<RepoConfigChange> localChanges = repoConfigChanges.filterBy(LOCAL);
        assertEquals(1, localChanges.size());
        assertTrue(localChanges.contains(repoConfigChange1));

        List<RepoConfigChange> federatedChanges = repoConfigChanges.filterBy(FEDERATED);
        assertEquals(1, federatedChanges.size());
        assertTrue(federatedChanges.contains(repoConfigChange2));
    }

    @Test
    void testFilterByRepoTypeAndAction() {
        List<RepoConfigChange> changes = repoConfigChanges.filterBy(REPO_CREATE, LOCAL);
        assertEquals(1, changes.size());
        assertTrue(changes.contains(repoConfigChange1));
    }

    @Test
    void testFilterByRepoTypeAndAction_NoChanges() {
        repoConfigChanges = new RepoConfigChanges(Map.of());
        List<RepoConfigChange> changes = repoConfigChanges.filterBy(REPO_CREATE, LOCAL);
        assertTrue(changes.isEmpty());
    }

    @Test
    void testFilterByRepoTypeAndMultipleActions() {
        RemoteRepoConfig remoteNew = RemoteRepoConfig.create("remote-new", Generic);
        RemoteRepoConfig remoteOld = RemoteRepoConfig.create("remote-upd", Generic);
        RemoteRepoConfig remoteUpdNew = RemoteRepoConfig.create("remote-upd", Generic);
        RemoteRepoConfig remoteDel = RemoteRepoConfig.create("remote-del", Generic);
        RepoConfigChange remoteCreate = RepoConfigChange.createOf(remoteNew);
        RepoConfigChange remoteUpdate = RepoConfigChange.updateOf(remoteOld, remoteUpdNew);
        RepoConfigChange remoteDelete = RepoConfigChange.deleteOf(remoteDel);
        Map<RepoDetailsType, List<RepoConfigChange>> map = new HashMap<>();
        map.put(REMOTE, Lists.newArrayList(remoteCreate, remoteUpdate, remoteDelete));
        RepoConfigChanges changes = RepoConfigChanges.of(new HashMap<>(map));

        List<RepoConfigChange> filtered = changes.filterBy(REMOTE, REPO_CREATE, REPO_UPDATE);

        assertEquals(2, filtered.size());
        assertTrue(filtered.contains(remoteCreate));
        assertTrue(filtered.contains(remoteUpdate));
        assertFalse(filtered.contains(remoteDelete));
    }

    @Test
    void testGetAll() {
        List<RepoConfigChange> allChanges = repoConfigChanges.getAll();
        assertEquals(2, allChanges.size());
        assertTrue(allChanges.contains(repoConfigChange1));
        assertTrue(allChanges.contains(repoConfigChange2));
    }

    @Test
    void testAdd() {
        RepoConfigChanges repoConfigChanges = new RepoConfigChanges(new HashMap<>());
        RepoConfigChange newChange =
                RepoConfigChange.createOf(LocalRepoConfig.create("repo3", Generic));
        repoConfigChanges.add(newChange);

        List<RepoConfigChange> localChanges = repoConfigChanges.filterBy(LOCAL);
        assertEquals(1, localChanges.size());
        assertTrue(localChanges.contains(newChange));
    }

    @Test
    void testGetMappedByType() {
        Map<RepoDetailsType, List<RepoConfigChange>> mappedByType = repoConfigChanges.getMappedByType();
        assertEquals(2, mappedByType.size());
        assertTrue(mappedByType.containsKey(LOCAL));
        assertTrue(mappedByType.containsKey(FEDERATED));
    }

    @Test
    void testFilterByRepoTypeAndPackageTypeAndAction() {
        RepoConfigChange repoConfigChange3 = RepoConfigChange.createOf(LocalRepoConfig.create("repo3", Maven));
        repoConfigChanges.add(repoConfigChange3);

        List<RepoConfigChange> changes = repoConfigChanges.filterBy(REPO_CREATE, LOCAL, Maven);
        assertEquals(1, changes.size());
        assertTrue(changes.contains(repoConfigChange3));
    }

    @Test
    void testFilterByRepoTypeAndPackageTypeAndAction_NoChanges() {
        List<RepoConfigChange> changes = repoConfigChanges.filterBy(REPO_CREATE, LOCAL, Maven);
        assertTrue(changes.isEmpty());
    }

    @Test
    void testCreateDeleteChangeOf() {
        RepoConfig repoConfig = LocalRepoConfig.create("repo4", Docker);
        RepoConfigChange deleteChange = RepoConfigChange.deleteOf(repoConfig);
        RepoConfigChanges deleteChanges = new RepoConfigChanges(deleteChange);

        List<RepoConfigChange> changes = deleteChanges.filterBy(REPO_DELETE);
        assertEquals(1, changes.size());
        assertEquals(repoConfig.getKey(), changes.get(0).getOldRepoConfig().getKey());
        assertEquals(REPO_DELETE, changes.get(0).getConfigAction());
    }

    @Test
    void testConstructorWithSingleChange() {
        RepoConfigChanges repoConfigChanges = new RepoConfigChanges(repoConfigChange1);
        List<RepoConfigChange> changes = repoConfigChanges.getAll();
        assertEquals(1, changes.size());
        assertTrue(changes.contains(repoConfigChange1));
    }

    @Test
    void testConstructorWithCollectionOfChanges() {
        Collection<RepoConfigChange> changesCollection = List.of(repoConfigChange1, repoConfigChange2);
        RepoConfigChanges repoConfigChanges = RepoConfigChanges.of(changesCollection);
        List<RepoConfigChange> changes = repoConfigChanges.getAll();
        assertEquals(2, changes.size());
        assertTrue(changes.contains(repoConfigChange1));
        assertTrue(changes.contains(repoConfigChange2));
    }

    @Test
    void testRemoveChange() {
        RepoConfigChanges repoConfigChanges = new RepoConfigChanges(repoConfigChange1);
        repoConfigChanges.remove(repoConfigChange1);
        List<RepoConfigChange> changes = repoConfigChanges.getAll();
        assertTrue(changes.isEmpty());
    }
}