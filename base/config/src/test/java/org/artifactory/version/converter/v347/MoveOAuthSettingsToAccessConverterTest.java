package org.artifactory.version.converter.v347;

import org.artifactory.config.AccessConfigHolder;
import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.access.client.model.OAuthSetting;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;

import java.util.List;

import static org.artifactory.config.AccessConfigKeys.OAUTH_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveOAuthSettingsToAccessConverterTest extends XmlConverterTest {
    private static final String RESOURCE_PATH = "/config/test/config.3.4.6_migrate_oauth_fields.xml";
    private static final String RESOURCE_PATH_DISABLED = "/config/test/config.3.4.6_migrate_oauth_fields_disabled.xml";
    private static final String RESOURCE_PATH_NOT_EXISTS =
            "/config/test/config.3.4.6_migrate_oauth_fields_not_exist.xml";

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
        Document doc = convertXml(RESOURCE_PATH, new MoveOAuthSettingsToAccessConverter());
        verifyContent(false, doc);
        OAuthGeneralSettings generalActual = AccessConfigHolder.getInstance().drainOAuthGeneralSettings();
        List<OAuthSetting> providerActual = AccessConfigHolder.getInstance().drainOAuthSetting();

        OAuthGeneralSettings generalExpected = OAuthGeneralSettings.builder()
                .enable(true)
                .defaultSetting("test-default").build();

        List<OAuthSetting> providerExpected = List.of(OAuthSetting.builder()
                        .name("test-default")
                        .clientId("client-id")
                        .enabled(true)
                        .providerType("github")
                        .clientSecret("secret")
                        .apiUrl("https://api.github.com/user")
                        .authUrl("https://github.com/login/oauth/authorize")
                        .tokenUrl("https://github.com/login/oauth/access_token")
                        .basicUrl("https://github.com/")
                        .domain("default-domain")
                        .autoUserCreation(true)
                        .allowUserToAccessProfile(true)
                        .central(false)
                        .pkce(true)
                        .useDefaultProxy(true)
                        .build(),
                OAuthSetting.builder()
                        .name("Artifactory")
                        .providerType("google")
                        .enabled(false)
                        .clientId("132670194298-hem0n91nlcq3u29brgbfoofgvgi469ta.apps.googleusercontent.com")
                        .clientSecret("PAU0_9Zo9LeZTtA7g4dWh4aF")
                        .apiUrl("https://www.googleapis.com/oauth2/v1/userinfo")
                        .authUrl("https://accounts.google.com/o/oauth2/auth")
                        .tokenUrl("https://www.googleapis.com/oauth2/v3/token")
                        .basicUrl("")
                        .domain("jfrog.com")
                        .central(false)
                        .autoUserCreation(true)
                        .allowUserToAccessProfile(true)
                        .pkce(false)
                        .useDefaultProxy(false)
                        .build()
        );
        assertEquals(generalExpected, generalActual);
        assertEquals(providerExpected, providerActual);
    }

    @Test
    void testConvertDisabled() throws Exception {
        verifyContent(true, getDocument(RESOURCE_PATH_DISABLED));
        Document doc = convertXml(RESOURCE_PATH_DISABLED, new MoveOAuthSettingsToAccessConverter());
        verifyContent(false, doc);
        OAuthGeneralSettings generalActual = AccessConfigHolder.getInstance().drainOAuthGeneralSettings();
        List<OAuthSetting> providerActual = AccessConfigHolder.getInstance().drainOAuthSetting();

        OAuthGeneralSettings generalExpected = OAuthGeneralSettings.builder()
                .enable(false)
                .defaultSetting("test-default").build();

        List<OAuthSetting> providerExpected = List.of(OAuthSetting.builder()
                        .name("test-default")
                        .clientId("client-id")
                        .enabled(true)
                        .providerType("github")
                        .clientSecret("secret")
                        .apiUrl("https://api.github.com/user")
                        .authUrl("https://github.com/login/oauth/authorize")
                        .tokenUrl("https://github.com/login/oauth/access_token")
                        .basicUrl("https://github.com/")
                        .domain("default-domain")
                        .autoUserCreation(false)
                        .allowUserToAccessProfile(false)
                        .central(false)
                        .pkce(true)
                        .useDefaultProxy(true)
                        .build(),
                OAuthSetting.builder()
                        .name("Artifactory")
                        .providerType("google")
                        .enabled(false)
                        .clientId("132670194298-hem0n91nlcq3u29brgbfoofgvgi469ta.apps.googleusercontent.com")
                        .clientSecret("PAU0_9Zo9LeZTtA7g4dWh4aF")
                        .apiUrl("https://www.googleapis.com/oauth2/v1/userinfo")
                        .authUrl("https://accounts.google.com/o/oauth2/auth")
                        .tokenUrl("https://www.googleapis.com/oauth2/v3/token")
                        .basicUrl("")
                        .domain("jfrog.com")
                        .central(false)
                        .autoUserCreation(false)
                        .allowUserToAccessProfile(false)
                        .pkce(false)
                        .useDefaultProxy(false)
                        .build()
        );
        assertEquals(generalExpected, generalActual);
        assertEquals(providerExpected, providerActual);
    }

    @Test
    void testConvertNotExists() throws Exception {
        verifyContent(true, getDocument(RESOURCE_PATH_NOT_EXISTS));
        Document doc = convertXml(RESOURCE_PATH_NOT_EXISTS, new MoveOAuthSettingsToAccessConverter());
        verifyContent(false, doc);
        OAuthGeneralSettings generalActual = AccessConfigHolder.getInstance().drainOAuthGeneralSettings();
        List<OAuthSetting> providerActual = AccessConfigHolder.getInstance().drainOAuthSetting();
        assertEquals(OAuthGeneralSettings.builder().enable(false).build(), generalActual);
        assertTrue(providerActual.isEmpty());
    }

    private void verifyContent(boolean expectedValue, Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element security = rootElement.getChild(SECURITY, namespace);

        Element oAuthSettingsParent = security.getChild(OAUTH_SETTINGS, namespace);
        if (oAuthSettingsParent != null) {
            assertTrue(expectedValue);
            Element enable = oAuthSettingsParent.getChild("enableIntegration", namespace);
            if (enable != null) {
                assertNotNull(enable.getValue());
            }
        } else {
            assertFalse(expectedValue);
        }
    }
}