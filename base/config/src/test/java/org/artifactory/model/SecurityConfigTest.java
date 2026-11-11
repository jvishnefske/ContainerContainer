package org.artifactory.model;

import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Amir Avidan
 */
class SecurityConfigTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void testSetXrayNotAvailableMode() {
        // Arrange
        SecurityConfig securityConfig = new SecurityConfig();
        XrayNotAvailableMode mode = XrayNotAvailableMode.FAIL_CLOSE;

        // Act
        securityConfig.setXrayNotAvailableMode(mode);

        // Assert
        assertEquals(mode, securityConfig.getXrayNotAvailableMode(), "XrayNotAvailableMode should be set correctly");
    }

    @Test
    void testGetXrayNotAvailableModeDefaultValue() {
        // Arrange
        SecurityConfig securityConfig = new SecurityConfig();

        // Act
        XrayNotAvailableMode mode = securityConfig.getXrayNotAvailableMode();

        // Assert
        assertNull(mode, "Default XrayNotAvailableMode should be null");
    }
}