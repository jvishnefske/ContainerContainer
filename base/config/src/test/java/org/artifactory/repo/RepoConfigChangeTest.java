package org.artifactory.repo;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.junit.jupiter.api.Test;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepoConfigChangeTest {

    @Test
    void testCreateOf() {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn("test-repo");

        RepoConfigChange change = RepoConfigChange.createOf(repoConfig);

        assertEquals("test-repo", change.getRepoKey());
        assertEquals(REPO_CREATE, change.getConfigAction());
        assertEquals(repoConfig, change.getNewRepoConfig());
        assertNull(change.getOldRepoConfig());
    }

    @Test
    void testUpdateOf() {
        RepoConfig oldRepoConfig = mock(RepoConfig.class);
        RepoConfig newRepoConfig = mock(RepoConfig.class);
        when(oldRepoConfig.getKey()).thenReturn("test-repo");

        RepoConfigChange change = RepoConfigChange.updateOf(oldRepoConfig, newRepoConfig);

        assertEquals("test-repo", change.getRepoKey());
        assertEquals(REPO_UPDATE, change.getConfigAction());
        assertEquals(newRepoConfig, change.getNewRepoConfig());
        assertEquals(oldRepoConfig, change.getOldRepoConfig());
    }

    @Test
    void testDeleteOf() {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn("test-repo");

        RepoConfigChange change = RepoConfigChange.deleteOf(repoConfig);

        assertEquals("test-repo", change.getRepoKey());
        assertEquals(REPO_DELETE, change.getConfigAction());
        assertNull(change.getNewRepoConfig());
        assertEquals(repoConfig, change.getOldRepoConfig());
    }

    @Test
    void testGetRepoType() {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);

        RepoConfigChange change = RepoConfigChange.createOf(repoConfig);

        assertEquals(RepoDetailsType.LOCAL, change.getRepoType());
    }

    @Test
    void testGetPackageType() {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);

        RepoConfigChange change = RepoConfigChange.createOf(repoConfig);

        assertEquals(RepoType.Maven, change.getPackageType());
    }

    @Test
    void testGetModificationForCreate() {
        RepoConfig newRepoConfig = mock(RepoConfig.class);
        when(newRepoConfig.getModification()).thenReturn(123L);

        RepoConfigChange change = RepoConfigChange.createOf(newRepoConfig);

        assertEquals(123L, change.getModification());
    }

    @Test
    void testGetModificationForDelete() {
        RepoConfig oldRepoConfig = mock(RepoConfig.class);
        when(oldRepoConfig.getModification()).thenReturn(456L);

        RepoConfigChange change = RepoConfigChange.deleteOf(oldRepoConfig);

        assertEquals(456L, change.getModification());
    }

    @Test
    void testGetModificationWithForUpdate() {
        RepoConfig oldRepoConfig = mock(RepoConfig.class);
        RepoConfig newRepoConfig = mock(RepoConfig.class);
        when(newRepoConfig.getModification()).thenReturn(789L);

        RepoConfigChange change = RepoConfigChange.updateOf(oldRepoConfig, newRepoConfig);

        assertEquals(789L, change.getModification());
    }

    @Test
    void testGetModificationWithNoConfigs() {
        RepoConfigChange change =
                new RepoConfigChange("repoKey", REPO_CREATE, null, null);

        assertEquals(0L, change.getModification());
    }

    @Test
    void testIsCreateOrUpdate() {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn("test-repo");

        RepoConfigChange createChange = RepoConfigChange.createOf(repoConfig);
        assertTrue(createChange.isCreateOrUpdate());

        RepoConfigChange updateChange = RepoConfigChange.updateOf(repoConfig, repoConfig);
        assertTrue(updateChange.isCreateOrUpdate());

        RepoConfigChange deleteChange = RepoConfigChange.deleteOf(repoConfig);
        assertFalse(deleteChange.isCreateOrUpdate());
    }
}