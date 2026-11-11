package org.artifactory.model;

import org.artifactory.repo.federated.FederatedMember;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.artifactory.repo.federated.FederatedMember.BASE_URL_PLACEHOLDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FederationConfig}.
 */
class FederationConfigTest {

    @Test
    void isFederated_shouldReturnFalse_whenNoMembersAndNotOnGrid() {
        // Arrange
        FederationConfig config = new FederationConfig();

        // Act & Assert
        assertFalse(config.isFederated());
    }

    @Test
    void isFederated_shouldReturnFalse_whenOnlyLocalMemberExists() {
        // Arrange - local member uses the BASE_URL_PLACEHOLDER
        FederationConfig config = new FederationConfig();
        FederatedMember localMember = createMember(BASE_URL_PLACEHOLDER + "/my-repo");
        config.setMembers(List.of(localMember));

        // Act & Assert - only the local/self member remains, repo is not truly federated
        assertFalse(config.isFederated());
    }

    @Test
    void isFederated_shouldReturnTrue_whenSingleRemoteMemberExists() {
        FederationConfig config = new FederationConfig();
        config.setMembers(List.of(createMember("http://remote-site.example.com/artifactory/my-repo")));

        assertTrue(config.isFederated());
    }

    @Test
    void isFederated_shouldReturnTrue_whenLocalAndRemoteMembersExist() {
        // Arrange
        FederationConfig config = new FederationConfig();
        FederatedMember localMember = createMember(BASE_URL_PLACEHOLDER + "/my-repo");
        FederatedMember remoteMember = createMember("http://remote-site.example.com/artifactory/my-repo");
        config.setMembers(List.of(localMember, remoteMember));

        // Act & Assert - has remote members, so it's federated
        assertTrue(config.isFederated());
    }

    @Test
    void isFederated_shouldReturnTrue_whenTwoRemoteMembersExist() {
        FederationConfig config = new FederationConfig();
        config.setMembers(List.of(
                createMember("http://site1.example.com/artifactory/my-repo"),
                createMember("http://site2.example.com/artifactory/my-repo")));

        assertTrue(config.isFederated());
    }

    @Test
    void isFederated_shouldReturnTrue_whenFederationOnGrid() {
        // Arrange
        FederationConfig config = new FederationConfig();
        config.setFederationOnGrid(true);

        // Act & Assert
        assertTrue(config.isFederated());
    }

    @Test
    void isFederated_shouldReturnFalse_whenSingleMemberHasNullUrl() {
        // Arrange - a member with null URL is not counted as a remote member
        FederationConfig config = new FederationConfig();
        FederatedMember member = new FederatedMember();
        config.setMembers(List.of(member));

        // Act & Assert
        assertFalse(config.isFederated());
    }

    @Test
    void copyConstructor_shouldCreateIndependentCopy() {
        // Arrange
        FederationConfig original = new FederationConfig();
        original.setGridTopologyId("topology-123");
        original.setFederationOnGrid(true);
        original.setModificationDate(12345L);
        FederatedMember member = createMember("http://site.example.com/repo");
        original.setMembers(List.of(member));

        // Act
        FederationConfig copy = new FederationConfig(original);

        assertEquals("topology-123", copy.getGridTopologyId());
        assertTrue(copy.isFederationOnGrid());
        assertEquals(12345L, copy.getModificationDate());
        assertEquals(1, copy.getMembers().size());
        assertNotSame(original.getMembers(), copy.getMembers());
    }

    @Test
    void copyConstructor_shouldHandleNullInput() {
        FederationConfig copy = new FederationConfig(null);

        assertNotNull(copy);
        assertFalse(copy.isFederated());
    }

    private FederatedMember createMember(String url) {
        FederatedMember member = new FederatedMember();
        member.setUrl(url);
        member.setMode("bidirectional");
        member.setEnabled(true);
        return member;
    }
}
