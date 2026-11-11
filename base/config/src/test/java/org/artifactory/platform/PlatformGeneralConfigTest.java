package org.artifactory.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Andrey Tashlitsky
 */
class PlatformGeneralConfigTest {

    @Test
    void testPlatformGeneralConfigDefaults() {
        PlatformGeneralConfig platformGeneralConfig = PlatformGeneralConfig.builder().build();
        assertTrue(platformGeneralConfig.isHelpLinksEnabled());
        assertEquals("dd-MM-yy HH:mm:ss z", platformGeneralConfig.getDateFormat());
        assertNull(platformGeneralConfig.getLogo());
    }

    @Test
    void testPlatformGeneralConfig() {
        PlatformGeneralConfig platformGeneralConfig = PlatformGeneralConfig.builder()
                .dateFormat("dd-MM-yy")
                .helpLinksEnabled(false)
                .logo("http://some.logo.com")
                .build();
        assertFalse(platformGeneralConfig.isHelpLinksEnabled());
        assertEquals("dd-MM-yy", platformGeneralConfig.getDateFormat());
        assertEquals("http://some.logo.com", platformGeneralConfig.getLogo());
    }
}
