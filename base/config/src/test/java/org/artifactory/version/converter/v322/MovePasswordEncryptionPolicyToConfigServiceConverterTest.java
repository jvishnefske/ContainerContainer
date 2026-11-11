package org.artifactory.version.converter.v322;

import com.google.gson.JsonParser;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Map;

import static org.artifactory.config.AccessConfigKeys.PASSWORD_SETTINGS_ENCRYPTION_POLICY;
import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.config.AccessConfigKeys.SECURITY_PASSWORD_SETTINGS;

/**
 * @author Mor Merhav
 */
class MovePasswordEncryptionPolicyToConfigServiceConverterTest extends XmlConverterTest {

    private static final String RESOURCE_PATH_SUPPORTED =
            "/config/test/config.3.2.2_movePasswordEncryptionPolicyToConfigServiceConverterSupported.xml";

    private static final String RESOURCE_PATH_FIELD_NOT_EXISTS =
            "/config/test/config.3.2.2_movePasswordEncryptionPolicyToConfigServiceConverterNotExists.xml";

    private static MockedStatic<AccessConfigHolder> staticAccessConfigHolder;

    @BeforeAll
    static void beforeAll() {
        staticAccessConfigHolder = Mockito.mockStatic(AccessConfigHolder.class);
        AccessConfigHolder spyAccessConfigHolder = Mockito.spy(AccessConfigHolder.class);
        //noinspection ResultOfMethodCallIgnored
        staticAccessConfigHolder.when(AccessConfigHolder::getInstance).thenReturn(spyAccessConfigHolder);
    }

    @AfterAll
    static void afterAll() {
        staticAccessConfigHolder.close();
    }

    @Test
    void testConvert() throws Exception {
        Document document =
                convertXml(RESOURCE_PATH_SUPPORTED, new MovePasswordEncryptionPolicyToConfigServiceConverter());
        Namespace namespace = document.getRootElement().getNamespace();
        Element security = document.getRootElement().getChild(SECURITY, namespace);
        Assertions.assertNotNull(security);
        Element passwordSettings = security.getChild(SECURITY_PASSWORD_SETTINGS, namespace);
        Assertions.assertNotNull(passwordSettings);
        Element encryptionPolicy = passwordSettings.getChild(PASSWORD_SETTINGS_ENCRYPTION_POLICY, namespace);
        Assertions.assertNull(encryptionPolicy);

        Map<String, Object> config = AccessConfigHolder.getInstance().drainConfig();
        String expectedConfigJson = "{\"security\":{\"authentication\":{\"password-encryption\":\"supported\"}}}";
        String actualConfigJson = JsonUtils.getInstance().writeValueAsString(config);
        Assertions.assertEquals(JsonParser.parseString(expectedConfigJson), JsonParser.parseString(actualConfigJson));
    }

    @Test
    void testConvertFieldNotExists() throws Exception {
        Document document =
                convertXml(RESOURCE_PATH_FIELD_NOT_EXISTS, new MovePasswordEncryptionPolicyToConfigServiceConverter());
        Namespace namespace = document.getRootElement().getNamespace();
        Element security = document.getRootElement().getChild(SECURITY, namespace);
        Assertions.assertNotNull(security);
        Element passwordSettings = security.getChild(SECURITY_PASSWORD_SETTINGS, namespace);
        Assertions.assertNotNull(passwordSettings);
        Element encryptionPolicy = passwordSettings.getChild(PASSWORD_SETTINGS_ENCRYPTION_POLICY, namespace);
        Assertions.assertNull(encryptionPolicy);

        Map<String, Object> config = AccessConfigHolder.getInstance().drainConfig();
        Assertions.assertTrue(config.isEmpty());
    }

}