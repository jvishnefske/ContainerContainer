package org.artifactory.version.converter.v326;

import org.artifactory.config.AccessConfigHolder;
import org.artifactory.config.AccessConfigKeys;
import org.artifactory.convert.XmlConverterTest;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.group.LdapGroupSetting;
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
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.testng.annotations.BeforeMethod;

import java.util.List;

import static org.artifactory.config.AccessConfigHolder.SECURITY_MAP;

public class MoveLdapSettingToConfigConverterTest extends XmlConverterTest {

    private static final String RESOURCE_PATH_TRUE =
            "/config/test/config.3.2.6_moveLdapSettingToConfigServiceConverterTrue.xml";

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

    @BeforeMethod
    void cleanBeforeTesting() {
        AccessConfigHolder.getInstance().drainLdapSetting();
        AccessConfigHolder.getInstance().drainLdapGroupSetting();
    }
    @Test
    void testConvertLdapSettingAuthTrue() throws Exception {
        Document document =
                convertXml(RESOURCE_PATH_TRUE, new MoveLdapSettingToConfigServiceConverter());
        Namespace namespace = document.getRootElement().getNamespace();
        Element security = document.getRootElement().getChild(SECURITY_MAP, namespace);
        Assertions.assertNotNull(security);

        List<LdapSetting> config = AccessConfigHolder.getInstance().drainLdapSetting();
        String actualConfigJson = JsonUtils.getInstance().writeValueAsString(config);
        String expectedConfigJson ="[{\"key\":\"ldap1\",\"enabled\":false," +
                "\"ldapUrl\":\"ldap://localhost:10389/ou=system\",\"search\":{\"searchFilter\":\"uid={0}\"," +
                "\"searchBase\":\"\",\"searchSubTree\":true,\"managerDn\":\"uid=admin, ou=system\"," +
                "\"managerPassword\":\"secret\"},\"autoCreateUser\":true,\"emailAttribute\":\"mail\"," +
                "\"ldapPoisoningProtection\":true,\"allowUserToAccessProfile\":false,\"pagingSupportEnabled\":true}," +
                "{\"key\":\"myldap\",\"enabled\":true,\"ldapUrl\":\"ldap://safd\",\"userDnPattern\":\"sd\"," +
                "\"search\":{\"searchSubTree\":true},\"autoCreateUser\":true,\"emailAttribute\":\"mail\"," +
                "\"ldapPoisoningProtection\":true,\"allowUserToAccessProfile\":false,\"pagingSupportEnabled\":true}]";

        JSONAssert.assertEquals(expectedConfigJson, actualConfigJson, JSONCompareMode.STRICT);

        Element ldapSettingsElement = security.getChild(AccessConfigKeys.SECURITY_LDAP_SETTINGS, namespace);
        Assertions.assertNull(ldapSettingsElement);

        security.removeContent();
    }

    @Test
    void testConvertLdapGroupSetting() throws Exception {
        Document document =
                convertXml(RESOURCE_PATH_TRUE, new MoveLdapGroupSettingToConfigServiceConverter());
        Namespace namespace = document.getRootElement().getNamespace();
        Element security = document.getRootElement().getChild(SECURITY_MAP, namespace);
        Assertions.assertNotNull(security);
        List<LdapGroupSetting> config = AccessConfigHolder.getInstance().drainLdapGroupSetting();

        Assertions.assertEquals(1, config.size());

        String actualConfigJson = JsonUtils.getInstance().writeValueAsString(config);
        String expectedConfigJson = """
                [
                  {
                    "name": "myldap2",
                    "groupBaseDn": "",
                    "groupNameAttribute": "cn",
                    "groupMemberAttribute": "uniqueMember",
                    "subTree": true,
                    "filter": "(objectClass=groupOfNames)",
                    "descriptionAttribute": "description",
                    "strategy": "STATIC",
                    "enabledLdap": "",
                    "forceAttributeSearch": false
                  }
                ]
                """;

        JSONAssert.assertEquals(expectedConfigJson, actualConfigJson, JSONCompareMode.STRICT);

        Element ldapSettingsElement = security.getChild(AccessConfigKeys.SECURITY_LDAP_GROUP_SETTINGS, namespace);
        Assertions.assertNull(ldapSettingsElement);

        security.removeContent();
    }

}
