package org.artifactory.model;

import org.artifactory.descriptor.repo.DockerApiVersion;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.TerraformType;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.artifactory.util.PackageTypeConfigFields.BLOCK_PUSHING_SCHEMA_1;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_API_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_TAG_RETENTION;
import static org.artifactory.util.PackageTypeConfigFields.SUPPRESS_POM_CONSISTENCY_CHECKS;
import static org.artifactory.util.PackageTypeConfigFields.TERRAFORM_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Mor Merhav
 */
class LocalRepoConfigTest extends ArtifactoryHomeBoundJUnitTest {

    @ParameterizedTest
    @EnumSource(RepoType.class)
    void mavenGroupDefaultValues(RepoType repoType) {
        if (repoType.isMavenGroup()) {
            LocalRepoConfig localRepoConfig = LocalRepoConfig.create("key", repoType);

            assertEquals(repoType, localRepoConfig.getPackageType());

            Map<String, String> packageTypeConfig = localRepoConfig.getPackageTypeConfig();
            assertEquals(1, packageTypeConfig.size());
            assertTrue(packageTypeConfig.containsKey(SUPPRESS_POM_CONSISTENCY_CHECKS.getKey()));

            String suppressPomConsistencyChecks = packageTypeConfig.get(SUPPRESS_POM_CONSISTENCY_CHECKS.getKey());
            assertTrue(Boolean.parseBoolean(suppressPomConsistencyChecks));
        }
    }

    @Test
    void dockerDefaultValues() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("key", RepoType.Docker);

        assertEquals(RepoType.Docker, localRepoConfig.getPackageType());

        Map<String, String> packageTypeConfig = localRepoConfig.getPackageTypeConfig();
        assertEquals(3, packageTypeConfig.size());
        assertTrue(packageTypeConfig.containsKey(DOCKER_API_VERSION.getKey()));
        assertTrue(packageTypeConfig.containsKey(DOCKER_TAG_RETENTION.getKey()));
        assertTrue(packageTypeConfig.containsKey(BLOCK_PUSHING_SCHEMA_1.getKey()));

        String dockerApiVersion = packageTypeConfig.get(DOCKER_API_VERSION.getKey());
        String dockerTagRetention = packageTypeConfig.get(DOCKER_TAG_RETENTION.getKey());
        String blockPushingSchema1 = packageTypeConfig.get(BLOCK_PUSHING_SCHEMA_1.getKey());

        assertEquals(DockerApiVersion.V2.name(), dockerApiVersion);
        assertEquals("1", dockerTagRetention);
        assertTrue(Boolean.parseBoolean(blockPushingSchema1));
    }

    @Test
    void terraformDefaultValues() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("key", RepoType.Terraform);

        assertEquals(RepoType.Terraform, localRepoConfig.getPackageType());

        Map<String, String> packageTypeConfig = localRepoConfig.getPackageTypeConfig();
        assertEquals(1, packageTypeConfig.size());
        assertTrue(packageTypeConfig.containsKey(TERRAFORM_TYPE.getKey()));

        String terraformType = packageTypeConfig.get(TERRAFORM_TYPE.getKey());
        assertEquals(TerraformType.MODULE.getName(), terraformType);
    }

    @Test
    void testFederatedToLocalRepoConfig() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("key", RepoType.Generic);
        LocalRepoConfig localRepoConfig = LocalRepoConfig.fromFederated(federatedRepoConfig);

        assertEquals(localRepoConfig.getKey(), federatedRepoConfig.getKey());
        assertEquals(localRepoConfig.getPackageType(), federatedRepoConfig.getPackageType());
        assertEquals(localRepoConfig.getCreation(), federatedRepoConfig.getCreation());
    }

    @Test
    void fromFederated_shouldPreserveFederationData_whenFederatedRepoHasMembers() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("test-repo", RepoType.Generic);
        FederatedMember member1 = new FederatedMember();
        member1.setUrl("http://site1.example.com/artifactory/test-repo");
        member1.setMode("bidirectional");
        member1.setEnabled(true);
        FederatedMember member2 = new FederatedMember();
        member2.setUrl("http://site2.example.com/artifactory/test-repo");
        member2.setMode("bidirectional");
        member2.setEnabled(false);
        federatedRepoConfig.getRepoTypeConfig().setFederatedMembers(List.of(member1, member2));

        LocalRepoConfig localRepoConfig = LocalRepoConfig.fromFederated(federatedRepoConfig);

        assertNotNull(localRepoConfig.getFederation());
        assertEquals(2, localRepoConfig.getFederation().getMembers().size());
        assertEquals("http://site1.example.com/artifactory/test-repo",
                localRepoConfig.getFederation().getMembers().get(0).getUrlOrTemplate());
        assertEquals("bidirectional", localRepoConfig.getFederation().getMembers().get(0).getMode());
        assertTrue(localRepoConfig.getFederation().getMembers().get(0).isEnabled());
        assertFalse(localRepoConfig.getFederation().getMembers().get(1).isEnabled());
    }

    @Test
    void fromFederated_shouldPreserveProxySettings_whenFederatedRepoHasProxy() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("test-repo", RepoType.Generic);
        federatedRepoConfig.getRepoTypeConfig().setProxyRef("my-proxy");
        federatedRepoConfig.getRepoTypeConfig().setDisableProxy(true);

        LocalRepoConfig localRepoConfig = LocalRepoConfig.fromFederated(federatedRepoConfig);

        assertNotNull(localRepoConfig.getFederation());
        assertEquals("my-proxy", localRepoConfig.getFederation().getProxy());
        assertTrue(localRepoConfig.getFederation().isDisableProxy());
    }

    @Test
    void fromFederated_shouldPreserveGridTopologyId_whenFederatedRepoHasGridTopologyId() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("test-repo", RepoType.Generic);
        federatedRepoConfig.getBaseConfig().setGridTopologyId("topology-123");

        LocalRepoConfig localRepoConfig = LocalRepoConfig.fromFederated(federatedRepoConfig);

        assertEquals("topology-123", localRepoConfig.getBaseConfig().getFederationConfig().getGridTopologyId());
    }

    @Test
    void testXrayNotAvailableMode() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("key", RepoType.Generic);
        assertNull(localRepoConfig.getXrayNotAvailableMode());

        localRepoConfig.getSecurityConfig().setXrayNotAvailableMode(XrayNotAvailableMode.FAIL_OPEN);
        assertEquals(XrayNotAvailableMode.FAIL_OPEN, localRepoConfig.getXrayNotAvailableMode());
    }

    @Test
    void testXrayNotAvailableModeForFederated() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("key", RepoType.Generic);
        assertNull(federatedRepoConfig.getXrayNotAvailableMode());

        federatedRepoConfig.getSecurityConfig().setXrayNotAvailableMode(XrayNotAvailableMode.FAIL_OPEN);
        assertEquals(XrayNotAvailableMode.FAIL_OPEN,
                federatedRepoConfig.getXrayNotAvailableMode());
    }
}
