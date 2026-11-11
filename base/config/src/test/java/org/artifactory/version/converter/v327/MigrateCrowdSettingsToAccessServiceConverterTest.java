package org.artifactory.version.converter.v327;

import org.artifactory.config.AccessConfigHolder;
import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jfrog.access.client.model.CrowdSetting;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MigrateCrowdSettingsToAccessServiceConverterTest  extends XmlConverterTest {
    private static final String RESOURCE_PATH =
            "/config/test/config.3.2.7_migrate_crowd_fields.xml";
    private static final String RESOURCE_PATH_NOT_EXISTS =
            "/config/test/config.3.2.7_migrate_crowd_fields_no_exist.xml";

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
        verifyRootExistsInXml(true, getDocument(RESOURCE_PATH));
        Document doc = convertXml(RESOURCE_PATH,
                new MigrateCrowdSettingsToAccessServiceConverter());
        verifyRootExistsInXml(false, doc);
        CrowdSetting actual = AccessConfigHolder.getInstance().drainCrowdSetting();
        CrowdSetting expected = CrowdSetting.builder()
                .enable(true)
                .sessionValidationInterval(2)
                .autoUserCreation(false)
                .allowUserToAccessProfile(true)
                .customCookieTokenKey("customCookieTokenKey")
                .serverUrl("http://localhost:8095/crowd/")
                .password("password")
                .overrideAllGroupsUponLogin(true)
                .directAuthentication(false)
                .useDefaultProxy(true)
                .applicationName("artifactory")
                .build();
        assertEquals(expected, actual);
    }

    @Test
    void testConvertNotExists() throws Exception {
        verifyRootExistsInXml(false, getDocument(RESOURCE_PATH_NOT_EXISTS));
        Document doc = convertXml(RESOURCE_PATH_NOT_EXISTS,
                new MigrateCrowdSettingsToAccessServiceConverter());
        verifyRootExistsInXml(false, doc);
        CrowdSetting actual = AccessConfigHolder.getInstance().drainCrowdSetting();
        assertEquals(null, actual);
    }

    private void verifyRootExistsInXml(boolean expectedValue, Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element security = rootElement.getChild("security", namespace);
        Element crowdSettings = security.getChild("crowdSettings", namespace);
        if (crowdSettings != null) {
            assertTrue(expectedValue);
        } else {
            assertFalse(expectedValue);
        }
    }
}
