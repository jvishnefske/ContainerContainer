package org.artifactory.model;

import org.artifactory.descriptor.repo.DockerApiVersion;
import org.artifactory.descriptor.repo.PomCleanupPolicy;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.federated.FederatedMember;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.artifactory.util.PackageTypeConfigFields.DEBIAN_DEFAULT_ARCHITECTURES;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_API_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.POM_REPOSITORY_REFERENCES_CLEANUP_POLICY;


/**
 * @author Mor Merhav
 */
class VirtualRepoConfigTest {

    @ParameterizedTest
    @EnumSource(RepoType.class)
    void mavenGroupDefaultValues(RepoType repoType) {
        if (repoType.isMavenGroup()) {
            VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("key", repoType);

            Assertions.assertEquals(repoType, virtualRepoConfig.getPackageType());

            Map<String, String> packageTypeConfig = virtualRepoConfig.getPackageTypeConfig();
            Assertions.assertEquals(1, packageTypeConfig.size());
            Assertions.assertTrue(packageTypeConfig.containsKey(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey()));

            String pomRepositoryReferencesCleanupPolicy =
                    packageTypeConfig.get(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey());
            Assertions.assertEquals(PomCleanupPolicy.discard_active_reference.name(),
                    pomRepositoryReferencesCleanupPolicy);
        }
    }

    @Test
    void debianDefaultValues() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("key", RepoType.Debian);

        Assertions.assertEquals(RepoType.Debian, virtualRepoConfig.getPackageType());

        Map<String, String> packageTypeConfig = virtualRepoConfig.getPackageTypeConfig();
        Assertions.assertEquals(1, packageTypeConfig.size());
        Assertions.assertTrue(packageTypeConfig.containsKey(DEBIAN_DEFAULT_ARCHITECTURES.getKey()));

        String debianDefaultArchitectures = packageTypeConfig.get(DEBIAN_DEFAULT_ARCHITECTURES.getKey());

        Assertions.assertEquals("i386,amd64", debianDefaultArchitectures);
    }

    @Test
    void dockerDefaultValues() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("key", RepoType.Docker);

        Assertions.assertEquals(RepoType.Docker, virtualRepoConfig.getPackageType());

        Map<String, String> packageTypeConfig = virtualRepoConfig.getPackageTypeConfig();
        Assertions.assertEquals(1, packageTypeConfig.size());
        Assertions.assertTrue(packageTypeConfig.containsKey(DOCKER_API_VERSION.getKey()));

        String dockerApiVersion = packageTypeConfig.get(DOCKER_API_VERSION.getKey());

        Assertions.assertEquals(DockerApiVersion.V2.name(), dockerApiVersion);
    }

    @Test
    void isFederated_shouldReturnFalse_whenNoFederationMembers() {
        // Arrange
        VirtualRepoConfig config = VirtualRepoConfig.create("test-virtual", RepoType.Generic);

        // Act
        boolean result = config.isFederated();

        // Assert
        Assertions.assertFalse(result);
    }

    @Test
    void isFederated_shouldReturnTrue_whenFederationMembersExist() {
        VirtualRepoConfig config = VirtualRepoConfig.create("test-virtual", RepoType.Generic);
        FederatedMember member1 = new FederatedMember();
        member1.setUrl("http://site1.example.com/virtual-repo");
        FederatedMember member2 = new FederatedMember();
        member2.setUrl("http://site2.example.com/virtual-repo");
        config.getBaseConfig().getFederationConfig().setMembers(List.of(member1, member2));
        Assertions.assertTrue(config.isFederated());
    }
}