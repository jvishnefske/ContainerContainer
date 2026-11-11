package org.artifactory.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Andrey Tashlitsky
 */
class PlatformSystemMessageConfigTest {

    @Test
    void testPlatformGeneralConfigDefaults() {
        PlatformSystemMessageConfig platformSystemMessageConfig = PlatformSystemMessageConfig.builder().build();
        assertFalse(platformSystemMessageConfig.isSystemMessageEnabled());
        assertNull(platformSystemMessageConfig.getSystemMessage());
        assertNull(platformSystemMessageConfig.getSystemMessageTitle());
        assertEquals("#429f46", platformSystemMessageConfig.getSystemMessageTitleColor());
    }

    @Test
    void testPlatformGeneralConfig() {
        PlatformSystemMessageConfig platformSystemMessageConfig = PlatformSystemMessageConfig.builder()
                .systemMessageEnabled(true)
                .systemMessage("Hello World!")
                .systemMessageTitle("COOL")
                .systemMessageTitleColor("#429f47")
                .build();
        assertTrue(platformSystemMessageConfig.isSystemMessageEnabled());
        assertEquals("Hello World!", platformSystemMessageConfig.getSystemMessage());
        assertEquals("COOL", platformSystemMessageConfig.getSystemMessageTitle());
        assertEquals("#429f47", platformSystemMessageConfig.getSystemMessageTitleColor());
    }
}
