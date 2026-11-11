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

import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.config.AccessConfigKeys.SECURITY_BASIC_AUTH_ENABLED;


/**
 * @author Mor Merhav
 */
class MoveBasicAuthenticationEnabledToConfigServiceConverterTest extends XmlConverterTest {

    private static final String RESOURCE_PATH_TRUE =
            "/config/test/config.3.2.2_moveBasicAuthEnabledToConfigServiceConverterTrue.xml";

    private static final String RESOURCE_PATH_FALSE =
            "/config/test/config.3.2.2_moveBasicAuthEnabledToConfigServiceConverterFalse.xml";

    private static final String RESOURCE_PATH_FIELD_NOT_EXISTS =
            "/config/test/config.3.2.2_moveBasicAuthEnabledToConfigServiceConverterNotExists.xml";

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
    void testConvertBasicAuthTrue() throws Exception {
        Document document =
                convertXml(RESOURCE_PATH_TRUE, new MoveBasicAuthenticationEnabledToConfigServiceConverter());
        Namespace namespace = document.getRootElement().getNamespace();
        Element security = document.getRootElement().getChild(SECURITY, namespace);
        Assertions.assertNotNull(security);
        Element basicAuthEnabled = security.getChild(SECURITY_BASIC_AUTH_ENABLED, namespace);
        Assertions.assertNull(basicAuthEnabled);

        Map<String, Object> config = AccessConfigHolder.getInstance().drainConfig();

        String expectedConfigJson = "{\"security\":{\"authentication\":{\"basic-authentication-enabled\":true}}}";
        String actualConfigJson = JsonUtils.getInstance().writeValueAsString(config);
        Assertions.assertEquals(JsonParser.parseString(expectedConfigJson), JsonParser.parseString(actualConfigJson));
    }

    @Test
    void testConvertBasicAuthFalse() throws Exception {
        Document document =
                convertXml(RESOURCE_PATH_FALSE, new MoveBasicAuthenticationEnabledToConfigServiceConverter());
        Namespace namespace = document.getRootElement().getNamespace();
        Element security = document.getRootElement().getChild(SECURITY, namespace);
        Assertions.assertNotNull(security);
        Element basicAuthEnabled = security.getChild(SECURITY_BASIC_AUTH_ENABLED, namespace);
        Assertions.assertNull(basicAuthEnabled);

        Map<String, Object> config = AccessConfigHolder.getInstance().drainConfig();

        String expectedConfigJson = "{\"security\":{\"authentication\":{\"basic-authentication-enabled\":false}}}";
        String actualConfigJson = JsonUtils.getInstance().writeValueAsString(config);
        Assertions.assertEquals(JsonParser.parseString(expectedConfigJson), JsonParser.parseString(actualConfigJson));
    }

    @Test
    void testConvertFieldNotExists() throws Exception {
        Document document =
                convertXml(RESOURCE_PATH_FIELD_NOT_EXISTS,
                        new MoveBasicAuthenticationEnabledToConfigServiceConverter());
        Namespace namespace = document.getRootElement().getNamespace();
        Element security = document.getRootElement().getChild(SECURITY, namespace);
        Assertions.assertNotNull(security);
        Element encryptionPolicy = security.getChild(SECURITY_BASIC_AUTH_ENABLED, namespace);
        Assertions.assertNull(encryptionPolicy);

        Map<String, Object> config = AccessConfigHolder.getInstance().drainConfig();
        Assertions.assertTrue(config.isEmpty());
    }
}