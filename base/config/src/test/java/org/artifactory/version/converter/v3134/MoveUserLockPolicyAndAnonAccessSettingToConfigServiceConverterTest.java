package org.artifactory.version.converter.v3134;

import org.artifactory.config.AccessConfigHolder;
import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testng.annotations.BeforeMethod;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveUserLockPolicyAndAnonAccessSettingToConfigServiceConverterTest extends XmlConverterTest {
    private static final String RESOURCE_PATH =
            "/config/test/config.3.1.32_removeAccessConfigAndAnonAccessEnabledAndUserLockPolicy.xml";
    private static final String RESOURCE_PATH_FALSE =
            "/config/test/config.3.1.32_removeAccessConfigAndAnonAccessEnabledAndUserLockPolicyFalse.xml";
    private static final String RESOURCE_PATH_NOT_EXISTS =
            "/config/test/config.3.1.32_removeAccessConfigAndAnonAccessEnabledAndUserLockPolicyNotExists.xml";

    private static MockedStatic<AccessConfigHolder> staticAccessConfigHolder;

    @BeforeAll
    static void beforeAll() {
        staticAccessConfigHolder = Mockito.mockStatic(AccessConfigHolder.class);
        AccessConfigHolder spyAccessConfigHolder = Mockito.spy(AccessConfigHolder.class);
        //noinspection ResultOfMethodCallIgnored
        staticAccessConfigHolder.when(AccessConfigHolder::getInstance).thenReturn(spyAccessConfigHolder);
    }

    @BeforeMethod
    void cleanup() {
        AccessConfigHolder.getInstance().drainConfig();
    }
    @AfterAll
    static void afterAll() {
        staticAccessConfigHolder.close();
    }

    @Test
    void testConvert() throws Exception {
        verifyContent(true, getDocument(RESOURCE_PATH));
        Document doc = convertXml(RESOURCE_PATH,
                new MoveUserLockPolicyAndAnonAccessSettingToConfigServiceConverter());
        verifyContent(false, doc);
        Map<String, Object> actual = AccessConfigHolder.getInstance().drainConfig();
        Map<String, Object> expected = Map.of("security",
                Map.of("anonymous-access-enabled", true,
                        "user-lock-policy", Map.of("attempts", 5L)));
        assertEquals(expected, actual);
    }

    @Test
    void testConvertFalse() throws Exception {
        verifyContent(true, getDocument(RESOURCE_PATH_FALSE));
        Document doc = convertXml(RESOURCE_PATH_FALSE,
                new MoveUserLockPolicyAndAnonAccessSettingToConfigServiceConverter());
        verifyContent(false, doc);
        Map<String, Object> actual = AccessConfigHolder.getInstance().drainConfig();
        Map<String, Object> expected = Map.of("security",
                Map.of("anonymous-access-enabled", false,
                        "user-lock-policy", Map.of("attempts", 0L)));
        assertEquals(expected, actual);
    }

    @Test
    void testConvertNotExists() throws Exception {
        verifyContent(true, getDocument(RESOURCE_PATH_NOT_EXISTS));
        Document doc = convertXml(RESOURCE_PATH_NOT_EXISTS,
                new MoveUserLockPolicyAndAnonAccessSettingToConfigServiceConverter());
        verifyContent(false, doc);
        Map<String, Object> actual = AccessConfigHolder.getInstance().drainConfig();
        Map<String, Object> expected = Map.of("security",
                Map.of("anonymous-access-enabled", false));
        assertEquals(expected, actual);
    }

    private void verifyContent(boolean expectedValue, Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element security = rootElement.getChild("security", namespace);
        Element userLockPolicy = security.getChild("userLockPolicy", namespace);
        if (userLockPolicy != null) {
            assertTrue(expectedValue);
        } else {
            assertFalse(expectedValue);
        }
        Element anonAccessEnabled = security.getChild("anonAccessEnabled", namespace);
        if (anonAccessEnabled != null) {
            assertTrue(expectedValue);
        } else {
            assertFalse(expectedValue);
        }
    }
}
