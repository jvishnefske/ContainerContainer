package org.artifactory.model;

import org.artifactory.repo.federated.FederatedMember;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.artifactory.repo.federated.FederatedMember.BASE_URL_PLACEHOLDER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BaseConfig}.
 */
class BaseConfigTest {

    @Test
    void isFederated_shouldReturnFalse_whenFederationConfigIsNull() {
        // Arrange
        BaseConfig config = new BaseConfig();
        config.setFederationConfig(null);
        // Act & Assert
        assertFalse(config.isFederated());
    }

    @Test
    void isFederated_shouldReturnFalse_whenFederationConfigHasNoMembers() {
        // Arrange
        BaseConfig config = new BaseConfig();
        config.getFederationConfig();

        // Act & Assert
        assertFalse(config.isFederated());
    }

    @Test
    void isFederated_shouldReturnTrue_whenFederationConfigHasRemoteMembers() {
        // Arrange
        BaseConfig config = new BaseConfig();
        FederatedMember member1 = new FederatedMember();
        member1.setUrl("http://site1.example.com/repo");
        FederatedMember member2 = new FederatedMember();
        member2.setUrl("http://site2.example.com/repo");
        config.getFederationConfig().setMembers(List.of(member1, member2));

        // Act & Assert
        assertTrue(config.isFederated());
    }

    @Test
    void isFederated_shouldReturnFalse_whenOnlyLocalMemberExists() {
        // Arrange - local member uses the BASE_URL_PLACEHOLDER
        BaseConfig config = new BaseConfig();
        FederatedMember localMember = new FederatedMember();
        localMember.setUrl(BASE_URL_PLACEHOLDER + "/my-repo");
        config.getFederationConfig().setMembers(List.of(localMember));

        // Act & Assert - only local/self member remains, not truly federated
        assertFalse(config.isFederated());
    }

    @Test
    void isFederated_shouldReturnTrue_whenFederationOnGridIsEnabled() {
        // Arrange
        BaseConfig config = new BaseConfig();
        config.getFederationConfig().setFederationOnGrid(true);
        config.getFederationConfig().setGridTopologyId("topology-123");

        // Act & Assert
        assertTrue(config.isFederated());
    }

    @Test
    void getFederationConfig_shouldAutoInitialize_whenNull() {
        // Arrange
        BaseConfig config = new BaseConfig();

        // Act
        FederationConfig federationConfig = config.getFederationConfig();

        // Assert
        assertNotNull(federationConfig);
    }

    @Test
    void setGridTopologyId_shouldDelegateToFederationConfig() {
        // Arrange
        BaseConfig config = new BaseConfig();

        // Act
        config.setGridTopologyId("my-topology");

        // Assert - verify it was set in FederationConfig
        assertTrue("my-topology".equals(config.getFederationConfig().getGridTopologyId()));
    }
}
