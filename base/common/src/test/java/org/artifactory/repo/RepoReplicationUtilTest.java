package org.artifactory.repo;

import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepoReplicationUtilTest {

    @Test
    void nullRepoConfigReturnsFalse() {
        assertThat(RepoReplicationUtil.hasEnabledReplication(null, mock(CentralConfigDescriptor.class))).isFalse();
    }

    @Test
    void nullDescriptorReturnsFalse() {
        assertThat(RepoReplicationUtil.hasEnabledReplication(mock(LocalRepoConfig.class), null)).isFalse();
    }

    @Test
    void localRepoWithEnabledReplicationReturnsTrue() {
        LocalRepoConfig repo = mock(LocalRepoConfig.class);
        when(repo.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repo.getKey()).thenReturn("local-repo");
        LocalReplicationDescriptor rep = mock(LocalReplicationDescriptor.class);
        when(rep.isEnabled()).thenReturn(true);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(descriptor.getMultiLocalReplications("local-repo")).thenReturn(List.of(rep));

        assertThat(RepoReplicationUtil.hasEnabledReplication(repo, descriptor)).isTrue();
    }

    @Test
    void localRepoWithNoEnabledReplicationReturnsFalse() {
        LocalRepoConfig repo = mock(LocalRepoConfig.class);
        when(repo.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repo.getKey()).thenReturn("local-repo");
        LocalReplicationDescriptor rep = mock(LocalReplicationDescriptor.class);
        when(rep.isEnabled()).thenReturn(false);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(descriptor.getMultiLocalReplications("local-repo")).thenReturn(List.of(rep));

        assertThat(RepoReplicationUtil.hasEnabledReplication(repo, descriptor)).isFalse();
    }

    @Test
    void federatedRepoWithEnabledReplicationReturnsTrue() {
        LocalRepoConfig repo = mock(LocalRepoConfig.class);
        when(repo.getRepoType()).thenReturn(RepoDetailsType.FEDERATED);
        when(repo.getKey()).thenReturn("fed-repo");
        LocalReplicationDescriptor rep = mock(LocalReplicationDescriptor.class);
        when(rep.isEnabled()).thenReturn(true);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(descriptor.getMultiLocalReplications("fed-repo")).thenReturn(List.of(rep));

        assertThat(RepoReplicationUtil.hasEnabledReplication(repo, descriptor)).isTrue();
    }

    @Test
    void remoteRepoWithEnabledReplicationReturnsTrue() {
        RemoteRepoConfig repo = mock(RemoteRepoConfig.class);
        when(repo.getRepoType()).thenReturn(RepoDetailsType.REMOTE);
        when(repo.getKey()).thenReturn("remote-repo");
        RemoteReplicationDescriptor rep = mock(RemoteReplicationDescriptor.class);
        when(rep.isEnabled()).thenReturn(true);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(descriptor.getRemoteReplication("remote-repo")).thenReturn(rep);

        assertThat(RepoReplicationUtil.hasEnabledReplication(repo, descriptor)).isTrue();
    }

    @Test
    void remoteRepoWithDisabledReplicationReturnsFalse() {
        RemoteRepoConfig repo = mock(RemoteRepoConfig.class);
        when(repo.getRepoType()).thenReturn(RepoDetailsType.REMOTE);
        when(repo.getKey()).thenReturn("remote-repo");
        RemoteReplicationDescriptor rep = mock(RemoteReplicationDescriptor.class);
        when(rep.isEnabled()).thenReturn(false);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(descriptor.getRemoteReplication("remote-repo")).thenReturn(rep);

        assertThat(RepoReplicationUtil.hasEnabledReplication(repo, descriptor)).isFalse();
    }

    @Test
    void remoteRepoWithNullReplicationReturnsFalse() {
        RemoteRepoConfig repo = mock(RemoteRepoConfig.class);
        when(repo.getRepoType()).thenReturn(RepoDetailsType.REMOTE);
        when(repo.getKey()).thenReturn("remote-repo");
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(descriptor.getRemoteReplication("remote-repo")).thenReturn(null);

        assertThat(RepoReplicationUtil.hasEnabledReplication(repo, descriptor)).isFalse();
    }

    @Test
    void virtualRepoReturnsFalse() {
        VirtualRepoConfig repo = mock(VirtualRepoConfig.class);
        when(repo.getRepoType()).thenReturn(RepoDetailsType.VIRTUAL);

        assertThat(RepoReplicationUtil.hasEnabledReplication(repo, mock(CentralConfigDescriptor.class))).isFalse();
    }
}
