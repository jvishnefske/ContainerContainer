package org.artifactory.version.converter.v325;

import org.artifactory.config.AccessConfigHolder;
import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jfrog.access.client.model.HttpSsoSetting;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MigrateSsoHttpToConfigServiceConverterTest extends XmlConverterTest {
    private static final String RESOURCE_PATH =
            "/config/test/config.3.2.5_migrate_httpSso_fields.xml";
    private static final String RESOURCE_PATH_NOT_EXISTS =
            "/config/test/config.3.2.5_migrate_httpSso_fields_no_exist.xml";

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
        verifyContent(true, getDocument(RESOURCE_PATH));
        Document doc = convertXml(RESOURCE_PATH,
                new MigrateSsoHttpToConfigServiceConverter());
        verifyContent(false, doc);
        HttpSsoSetting actual = AccessConfigHolder.getInstance().drainHttpSsoSetting();
        HttpSsoSetting expected = HttpSsoSetting.builder()
                .httpSsoProxied(true)
                .allowUserToAccessProfile(true)
                .autoCreateUser(false)
                .syncLdapGroups(true)
                .remoteUserRequestVariable("REMOTE_USER")
                .build();
        assertEquals(expected, actual);
    }

    @Test
    void testConvertNotExists() throws Exception {
        verifyContent(false, getDocument(RESOURCE_PATH_NOT_EXISTS));
        convertXml(RESOURCE_PATH_NOT_EXISTS,
                new MigrateSsoHttpToConfigServiceConverter());
        HttpSsoSetting actual = AccessConfigHolder.getInstance().drainHttpSsoSetting();
        assertEquals(null, actual);
    }

    private void verifyContent(boolean expectedValue, Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element security = rootElement.getChild("security", namespace);
        Element userLockPolicy = security.getChild("httpSsoSettings", namespace);
        if (userLockPolicy != null) {
            assertTrue(expectedValue);
        } else {
            assertFalse(expectedValue);
        }
    }
}
