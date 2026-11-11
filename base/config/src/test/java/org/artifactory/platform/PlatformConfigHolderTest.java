package org.artifactory.platform;

import com.google.gson.JsonParser;
import org.jfrog.access.client.platformconfig.ConfigurationModel;
import org.jfrog.common.JsonUtils;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Andrey Tashlitsky
 */
class PlatformConfigHolderTest {

    private PlatformConfigHolder platformConfigHolder;

    @Test
    void addValuesAndDrainConfig() {
        PlatformGeneralConfig platformGeneralConfig = PlatformGeneralConfig.builder()
                .dateFormat("dd-MM-yy")
                .helpLinksEnabled(false)
                .logo("http://www.rw-designer.com/icon-image/21555-128x128x32.png")
                .build();
        PlatformSystemMessageConfig platformSystemMessageConfig = PlatformSystemMessageConfig.builder()
                .systemMessageEnabled(false)
                .systemMessage("Hello World!")
                .systemMessageTitle("COOL")
                .systemMessageTitleColor("#429f46")
                .build();
        assertTrue(platformConfigHolder.isEmpty());
        platformConfigHolder.addPlatformGeneralConfig(platformGeneralConfig);
        platformConfigHolder.addPlatformSystemMessageConfig(platformSystemMessageConfig);
        assertFalse(platformConfigHolder.isEmpty());
        Map<String, ConfigurationModel> config = platformConfigHolder.drainConfig();
        String expectedJson = ResourceUtils.getResourceAsString("/platform/platform_config.json");
        String actualJson = JsonUtils.getInstance().writeValueAsString(config);
        assertEquals(JsonParser.parseString(expectedJson), JsonParser.parseString(actualJson));
        assertTrue(platformConfigHolder.isEmpty());
    }

    @BeforeEach
    void resetConfigHolder() {
        platformConfigHolder = PlatformConfigHolder.getInstance();
        platformConfigHolder.drainConfig();
    }
}
