package org.artifactory.version.converter.v3134;

import org.artifactory.config.AccessConfigHolder;
import org.artifactory.convert.XmlConverterTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopyPasswordExpirationPolicyToConfigServiceConverterTest extends XmlConverterTest {
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

    @AfterAll
    static void afterAll() {
        staticAccessConfigHolder.close();
    }

    @Test
    void testConvert() throws Exception {
        convertXml(RESOURCE_PATH,
                new CopyPasswordExpirationPolicyToConfigServiceConverter());
        Map<String, Object> actual = AccessConfigHolder.getInstance().drainConfig();
        Map<String, Object> expected = Map.of("security",
                Map.of("user-lock-policy", Map.of("password-expiry-days", 60L)));
        assertEquals(expected, actual);
    }

    @Test
    void testConvertFalse() throws Exception {
        convertXml(RESOURCE_PATH_FALSE,
                new CopyPasswordExpirationPolicyToConfigServiceConverter());
        Map<String, Object> actual = AccessConfigHolder.getInstance().drainConfig();
        Map<String, Object> expected = Map.of("security",
                Map.of("user-lock-policy", Map.of("password-expiry-days", 0L)));
        assertEquals(expected, actual);
    }

    @Test
    void testConvertNotExists() throws Exception {
        convertXml(RESOURCE_PATH_NOT_EXISTS,
                new CopyPasswordExpirationPolicyToConfigServiceConverter());
        Map<String, Object> actual = AccessConfigHolder.getInstance().drainConfig();
        assertTrue(actual.isEmpty());
    }
}
