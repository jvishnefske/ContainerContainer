package org.artifactory.model;

import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.artifactory.repo.xray.XrayNotAvailableMode.FAIL_CLOSE;
import static org.artifactory.repo.xray.XrayNotAvailableMode.FAIL_OPEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Amir Avidan
 */
class XrayRepoConfigTest extends ArtifactoryHomeBoundJUnitTest {

    @AfterEach
    void tearDown() {
        unbindArtifactoryHome();
    }

    @Test
    void testDefaultValues() {
        XrayRepoConfig config = new XrayRepoConfig();
        assertFalse(config.isEnabled());
        assertNull(config.getXrayNotAvailableMode());
        assertTrue(config.getDataTtl() > 0);
    }

    @Test
    void testSetXrayNotAvailableModeDirectly() {
        XrayRepoConfig config = new XrayRepoConfig();
        config.setXrayNotAvailableMode(FAIL_CLOSE);
        assertEquals(FAIL_CLOSE, config.getXrayNotAvailableMode());
    }

    @Test
    void testXrayNotAvailableModeFromStringFailClose() {
        XrayRepoConfig config = new XrayRepoConfig();
        config.xrayNotAvailableModeFromString("fail_close");
        assertEquals(FAIL_CLOSE, config.getXrayNotAvailableMode());
    }

    @Test
    void testXrayNotAvailableModeFromStringFailOpen() {
        XrayRepoConfig config = new XrayRepoConfig();
        config.xrayNotAvailableModeFromString("fail_open");
        assertEquals(FAIL_OPEN, config.getXrayNotAvailableMode());
    }

    @Test
    void testXrayNotAvailableModeFromStringDefault() {
        XrayRepoConfig config = new XrayRepoConfig();
        config.xrayNotAvailableModeFromString("unknown");
        assertEquals(XrayNotAvailableMode.USE_SYSTEM_PROPERTY, config.getXrayNotAvailableMode());
    }

    @Test
    void testEqualsAndHashCode() {
        // Create two identical objects
        org.artifactory.descriptor.repo.XrayRepoConfigDescriptor config1 =
                new org.artifactory.descriptor.repo.XrayRepoConfigDescriptor();
        config1.setEnabled(true);
        config1.setDataTtl(90);
        config1.setXrayNotAvailableMode(XrayNotAvailableMode.FAIL_CLOSE);

        org.artifactory.descriptor.repo.XrayRepoConfigDescriptor config2 =
                new org.artifactory.descriptor.repo.XrayRepoConfigDescriptor();
        config2.setEnabled(true);
        config2.setDataTtl(90);
        config2.setXrayNotAvailableMode(XrayNotAvailableMode.FAIL_CLOSE);

        // Verify equals and hashCode for identical objects
        assertEquals(config1, config2, "Objects with the same values should be equal");
        assertEquals(config1.hashCode(), config2.hashCode(), "Hash codes should match for equal objects");

        // Modify one object
        config2.setEnabled(false);

        // Verify equals and hashCode for different objects
        assertNotEquals(config1, config2, "Objects with different values should not be equal");
        assertNotEquals(config1.hashCode(), config2.hashCode(), "Hash codes should not match for unequal objects");
    }
}
