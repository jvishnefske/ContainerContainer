package org.artifactory.repo.federated;

import org.artifactory.repo.FederatedMemberConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederatedMemberTest {

    @Test
    void from_shouldCreateMember_whenConfigHasAllFields() {
        // Arrange
        FederatedMemberConfiguration config = new FederatedMemberConfiguration(
                "http://server/repo", "READ_WRITE", true
        );

        // Act
        FederatedMember result = FederatedMember.from(config);

        // Assert
        assertEquals("http://server/repo", result.getUrlOrTemplate());
        assertEquals("READ_WRITE", result.getMode());
        assertTrue(result.isEnabled());
    }

    @Test
    void from_shouldSetEnabledTrue_whenConfigEnabledIsNull() {
        // Arrange - enabled defaults to null when using default constructor
        FederatedMemberConfiguration config = new FederatedMemberConfiguration();
        config.setUrl("http://server/repo");
        config.setMode("READ_ONLY");
        config.setEnabled(null);

        // Act
        FederatedMember result = FederatedMember.from(config);

        // Assert - enabled should default to true when null
        assertTrue(result.isEnabled());
    }

    @Test
    void from_shouldSetEnabledFalse_whenConfigEnabledIsFalse() {
        // Arrange
        FederatedMemberConfiguration config = new FederatedMemberConfiguration(
                "http://server/repo", "READ_ONLY", false
        );

        // Act
        FederatedMember result = FederatedMember.from(config);

        // Assert
        assertFalse(result.isEnabled());
    }

    @Test
    void toDescriptor_shouldConvertToDescriptorMember() {
        // Arrange
        FederatedMember member = new FederatedMember();
        member.setUrlOrTemplate("http://server/repo");
        member.setMode("READ_WRITE");
        member.setEnabled(true);

        // Act
        org.artifactory.descriptor.repo.FederatedMember descriptor = member.toDescriptor();

        // Assert
        assertEquals("http://server/repo", descriptor.getUrlOrTemplate());
        assertEquals("READ_WRITE", descriptor.getMode());
        assertTrue(descriptor.isEnabled());
    }
}
