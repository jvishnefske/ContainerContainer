package org.artifactory.model;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for AggregatedReposConfig, specifically for federated to local conversion during export.
 */
class AggregatedReposConfigTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void setRepoConfigsByType_shouldConvertFederatedToLocal_whenFederatedRepoProvided() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("fed-repo", RepoType.Generic);
        federatedRepoConfig.getBaseConfig().setGridTopologyId("topology-123");
        FederatedMember member = new FederatedMember();
        member.setUrl("http://site1.example.com/artifactory/fed-repo");
        member.setMode("bidirectional");
        member.setEnabled(true);
        federatedRepoConfig.getRepoTypeConfig().setFederatedMembers(List.of(member));
        federatedRepoConfig.getRepoTypeConfig().setProxyRef("my-proxy");
        federatedRepoConfig.getRepoTypeConfig().setDisableProxy(true);

        AggregatedReposConfig aggregatedConfig = new AggregatedReposConfig(List.of(federatedRepoConfig));

        assertEquals(1, aggregatedConfig.getLocalRepoConfigs().size());
        assertTrue(aggregatedConfig.getFederatedRepoConfigs().isEmpty());

        LocalRepoConfig localRepoConfig = aggregatedConfig.getLocalRepoConfigs().get(0);
        assertEquals("fed-repo", localRepoConfig.getKey());
        assertEquals(RepoType.Generic, localRepoConfig.getPackageType());
        assertEquals("topology-123", localRepoConfig.getBaseConfig().getFederationConfig().getGridTopologyId());

        assertNotNull(localRepoConfig.getFederation());
        assertEquals(1, localRepoConfig.getFederation().getMembers().size());
        assertEquals("http://site1.example.com/artifactory/fed-repo",
                localRepoConfig.getFederation().getMembers().get(0).getUrlOrTemplate());
        assertEquals("my-proxy", localRepoConfig.getFederation().getProxy());
        assertTrue(localRepoConfig.getFederation().isDisableProxy());
    }

    @Test
    void setRepoConfigsByType_shouldKeepLocalRepoAsIs_whenLocalRepoProvided() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.Generic);
        localRepoConfig.getBaseConfig().setDescription("test description");

        AggregatedReposConfig aggregatedConfig = new AggregatedReposConfig(List.of(localRepoConfig));

        assertEquals(1, aggregatedConfig.getLocalRepoConfigs().size());
        assertTrue(aggregatedConfig.getFederatedRepoConfigs().isEmpty());
        assertEquals("local-repo", aggregatedConfig.getLocalRepoConfigs().get(0).getKey());
        assertEquals("test description",
                aggregatedConfig.getLocalRepoConfigs().get(0).getBaseConfig().getDescription());
    }

    @Test
    void setRepoConfigsByType_shouldHandleMixedRepoTypes_whenMultipleReposProvided() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.Generic);
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("fed-repo", RepoType.Maven);
        federatedRepoConfig.getBaseConfig().setGridTopologyId("topology-mixed");
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote-repo", RepoType.Npm);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.Docker);

        AggregatedReposConfig aggregatedConfig = new AggregatedReposConfig(
                List.of(localRepoConfig, federatedRepoConfig, remoteRepoConfig, virtualRepoConfig));

        assertEquals(2, aggregatedConfig.getLocalRepoConfigs().size());
        assertTrue(aggregatedConfig.getFederatedRepoConfigs().isEmpty());
        assertEquals(1, aggregatedConfig.getRemoteRepoConfigs().size());
        assertEquals(1, aggregatedConfig.getVirtualRepoConfigs().size());

        LocalRepoConfig convertedFed = aggregatedConfig.getLocalRepoConfigs().stream()
                .filter(r -> "fed-repo".equals(r.getKey()))
                .findFirst()
                .orElse(null);
        assertNotNull(convertedFed);
        assertEquals(RepoType.Maven, convertedFed.getPackageType());
        assertEquals("topology-mixed", convertedFed.getBaseConfig().getFederationConfig().getGridTopologyId());
    }

    @Test
    void setRepoConfigsByType_shouldHandleNullOrEmptyCollection() {
        AggregatedReposConfig nullConfig = new AggregatedReposConfig(null);
        assertTrue(nullConfig.getLocalRepoConfigs().isEmpty());
        assertTrue(nullConfig.getFederatedRepoConfigs().isEmpty());

        AggregatedReposConfig emptyConfig = new AggregatedReposConfig(List.of());
        assertTrue(emptyConfig.getLocalRepoConfigs().isEmpty());
        assertTrue(emptyConfig.getFederatedRepoConfigs().isEmpty());
    }

    @Test
    void setRepoConfigsByType_shouldPreserveProxyOnly_whenFederatedRepoHasNoMembers() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("fed-repo-no-members", RepoType.Generic);
        federatedRepoConfig.getRepoTypeConfig().setProxyRef("proxy-only");

        AggregatedReposConfig aggregatedConfig = new AggregatedReposConfig(List.of(federatedRepoConfig));

        LocalRepoConfig localRepoConfig = aggregatedConfig.getLocalRepoConfigs().get(0);
        assertNotNull(localRepoConfig.getFederation());
        assertEquals("proxy-only", localRepoConfig.getFederation().getProxy());
        assertTrue(localRepoConfig.getFederation().getMembers() == null ||
                localRepoConfig.getFederation().getMembers().isEmpty());
    }
}
