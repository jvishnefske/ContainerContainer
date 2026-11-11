package org.artifactory.version.converter.v334;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jfrog.access.client.model.SamlSetting;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MoveSamlSettingToAccessConverterTest extends XmlConverterTest {

    private static final String RESOURCE_PATH =
            "/config/test/config.3.3.4_migrate_saml_fields.xml";
    private static final String RESOURCE_PATH_NOT_EXIST =
            "/config/test/config.3.3.4_migrate_saml_not_exist.xml";
    private static final String RESOURCE_PATH_DEFAULT =
            "/config/test/config.3.3.4_migrate_saml_default.xml";
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
        ArtifactoryHome.unbind();
    }

    @Test
    void testConvert() throws Exception {
        ArtifactoryHome.bind(getOrCreateArtifactoryHomeStub());
        Document doc = convertXml(RESOURCE_PATH,
                new MoveSamlSettingToAccessConverter());
        SamlSetting actual = AccessConfigHolder.getInstance().drainSamlSetting();

        SamlSetting expected = SamlSetting
                .builder()
                .enableIntegration(true)
                .loginUrl("loginUrl")
                .logoutUrl("logoutUrl")
                .serviceProviderName("serviceProviderName")
                .certificate("certificate")
                .useEncryptedAssertion(true)
                .syncGroups(true)
                .groupAttribute("groupAttribute")
                .emailAttribute("emailAttribute")
                .autoUserCreation(true)
                .allowUserToAccessProfile(true)
                .autoRedirect(true)
                .verifyAudienceRestriction(true)
                .build();

        assertEquals(expected, actual);
    }

    @Test
    void testConvertNotExists() throws Exception {
        ArtifactoryHome.bind(getOrCreateArtifactoryHomeStub());
        Document doc = convertXml(RESOURCE_PATH_NOT_EXIST,
                new MoveSamlSettingToAccessConverter());
        SamlSetting actual = AccessConfigHolder.getInstance().drainSamlSetting();

        assertEquals(null, actual);
    }

    @Test
    void testConvertDefault() throws Exception {
        ArtifactoryHome.bind(getOrCreateArtifactoryHomeStub());
        Document doc = convertXml(RESOURCE_PATH_DEFAULT,
                new MoveSamlSettingToAccessConverter());
        SamlSetting actual = AccessConfigHolder.getInstance().drainSamlSetting();

        SamlSetting expected = SamlSetting
                .builder()
                .verifyAudienceRestriction(true)
                .autoUserCreation(true)
                .groupAttribute("")
                .certificate("")
                .emailAttribute("")
                .loginUrl("")
                .logoutUrl("")
                .serviceProviderName("")
                .build();

        assertEquals(expected, actual);
    }
}
