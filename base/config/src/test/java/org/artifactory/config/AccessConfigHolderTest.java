package org.artifactory.config;

import com.google.gson.JsonParser;
import org.artifactory.descriptor.security.EncryptionPolicy;
import org.jfrog.common.JsonUtils;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * @author Mor Merhav
 */
class AccessConfigHolderTest {

    @Test
    void addValuesAndDrainConfig() {
        AccessConfigHolder accessConfigHolder = new AccessConfigHolder();
        accessConfigHolder.addPasswordEncryptionPolicy(EncryptionPolicy.REQUIRED);
        accessConfigHolder.addDisableRememberMe(true);
        accessConfigHolder.addLoginAttempts(1);
        accessConfigHolder.addAnonAccessEnabled(true);
        accessConfigHolder.addBasicAuthenticationEnabled(true);
        accessConfigHolder.addDisableRememberMe(true);

        Map<String, Object> config = accessConfigHolder.drainConfig();

        String expectedJson = ResourceUtils.getResourceAsString("/config/access/config_for_access.json");
        String actualJson = JsonUtils.getInstance().writeValueAsString(config);
        Assertions.assertEquals(JsonParser.parseString(expectedJson), JsonParser.parseString(actualJson));
    }
}