package org.artifactory.security.ldap;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.LdapGroupAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.api.security.ldap.LdapService;
import org.artifactory.api.security.ldap.LdapSettingAndUser;
import org.artifactory.api.security.ldap.LdapUser;
import org.artifactory.api.security.ldap.LdapUserAttributes;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.SearchPattern;
import org.artifactory.model.security.UserImpl;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.springframework.ldap.core.NameAwareAttributes;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.expectThrows;

/**
 * @author BarakH
 */
public class LdapUtilsTest extends ArtifactoryHomeBoundTest {

    @Test(dataProvider = "provideAttributes")
    public void testFindSettingsForActiveUser(String attrID, String attrVal, boolean expectedNull) {
        String userName = "user";
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        MutableCentralConfigDescriptor descriptor = mock(MutableCentralConfigDescriptor.class);
        SecurityDescriptor securityDescriptor = mock(SecurityDescriptor.class);
        AccessService accessService = mock(AccessService.class);
        LdapSetting ldapSetting = mock(LdapSetting.class);
        when(accessService.getLdapSettings()).thenReturn(Collections.singletonList(ldapSetting));
        when(descriptor.getSecurity()).thenReturn(securityDescriptor);
        when(centralConfigService.getMutableDescriptor()).thenReturn(descriptor);
        LdapService ldapService = mock(LdapService.class);
        LdapUser ldapUser = new LdapUser(userName, userName);
        Attributes attributes = new BasicAttributes();
        attributes.put(attrID, attrVal);
        attributes.put("lockoutDuration", "-600000000");
        LdapUserAttributes ldapUserAttributes = new LdapUserAttributes(ldapUser, attributes);
        when(ldapService.getUserWithAttributesFromDomain(ldapSetting, userName)).thenReturn(ldapUserAttributes);
        when(ldapSetting.isEnabled()).thenReturn(true);
        LdapSettingAndUser result = LdapUtils.findSettingAndUser(userName,
                accessService, ldapService);
        if (expectedNull) {
            Assert.assertNull(result);
        } else {
            Assert.assertNotNull(result);
        }
    }

    @DataProvider
    private static Object[][] provideAttributes() {
        return new Object[][] {
                {"lockoutTime", "0", false},
                {"lockoutTime", "132282298867991751", false},
                {"lockoutTime", "999999999999999999", true},
                {"notAnAttribute", "val", false},
                {"pwdAccountLockedTime", "1234", true},
                {"userAccountControl", "66082", true},
                {"userAccountControl", "66050", true},
                {"userAccountControl", "514", true},
                {"userAccountControl", "546", true},
                {"userAccountControl", "262658", true},
                {"userAccountControl", "262690", true},
                {"userAccountControl", "328194", true},
                {"userAccountControl", "328226", true},
                {"userAccountControl", "544", false},
                {"userAccountControl", "66048", false},
                {"userAccountControl", "262656", false},
        };
    }

    @Test(dataProvider = "userAndEmail")
    public void createSimpleUserTest(String userName, String mailAttribute, boolean validAttribute,
            String email, boolean validEmail) throws NamingException {
        LdapUser ldapUser = new LdapUser(userName, userName);
        Attributes attributes = new NameAwareAttributes();
        if (validEmail && mailAttribute != null) {
            attributes.put(mailAttribute, email);
        }
        LdapUserAttributes userAttributes = new LdapUserAttributes(ldapUser, attributes);
        LdapSetting ldapSetting = mock(LdapSetting.class);
        when(ldapSetting.getEmailAttribute()).thenReturn(mailAttribute);
        LdapGroupAddon ldapGroupAddon = mock(LdapGroupAddon.class);
        UserGroupService userGroupService = mock(UserGroupService.class);
        UserInfo userInfo = new UserImpl(userName);
        when(userGroupService.findOrCreateExternalAuthUser(userName, !ldapSetting.isAutoCreateUser(),
                ldapSetting.isAllowUserToAccessProfile())).thenReturn(userInfo);
        SimpleUser user = LdapUtils.createSimpleUser(userName, ldapSetting, userAttributes,
                ldapGroupAddon, userGroupService);
        assertSame(user.getUsername(), userName);
        if (!validEmail || !validAttribute) {
            assertNull(user.getEmail());
        } else {
            assertEquals(user.getEmail(), userAttributes.getAttributes().get(mailAttribute).get().toString());
        }
    }

    @DataProvider
    private static Object[][] userAndEmail() {
        return new Object[][] {
                {"arielk", "mail", true, "arielk@jfrog.com", true},
                {"TestUser", "email", true, "testUsermail@blabla.com", true},
                {"TestUser", null, false, "testUsermail@blabla.com", true},
                {"user1421", " ", false, "user1421@gma.com", true},
                {"user@user.com", "mail", true, null, false},
                {"user1431", "mail", true, "   ", false},
                {"user1431", " ", false, "   ", false},
                {"user1431", null, false, null, false},
        };
    }

    @Test
    public void createSimpleUserThrowNamingExceptionTest() throws NamingException {
        final String userName = "arielk";
        final String mailAttribute = "mail";

        LdapUser ldapUser = new LdapUser(userName, userName);
        Attributes attributes = mock(BasicAttributes.class);
        LdapUserAttributes userAttributes = new LdapUserAttributes(ldapUser, attributes);
        LdapSetting ldapSetting = mock(LdapSetting.class);
        when(ldapSetting.getEmailAttribute()).thenReturn(mailAttribute);
        LdapGroupAddon ldapGroupAddon = mock(LdapGroupAddon.class);
        UserGroupService userGroupService = mock(UserGroupService.class);
        UserInfo userInfo = new UserImpl(userName);
        when(userGroupService.findOrCreateExternalAuthUser(userName, !ldapSetting.isAutoCreateUser(),
                ldapSetting.isAllowUserToAccessProfile())).thenReturn(userInfo);
        Attribute mockedMailAttribute = mock(Attribute.class);
        when(attributes.get(mailAttribute)).thenReturn(mockedMailAttribute);
        when(mockedMailAttribute.get()).thenThrow(NamingException.class);
        SimpleUser user = LdapUtils.createSimpleUser(userName, ldapSetting, userAttributes,
                ldapGroupAddon, userGroupService);
        assertEquals(user.getUsername(), userName);
        assertEquals(user.getDescriptor().getRealm(), LdapService.REALM);
        assertNull(user.getEmail());
    }

    /*
    Tests the refreshUserFromLdap method by:
    1. If there are no Ldap Settings - (a) expected exception is thrown, (b) we are not searching the user in LDAP.
    2. If there are Ldap Settings - (a) the user is created as expected,
            (b) we are searching for the user only once in LDAP.
     */
    @Test(dataProvider = "userAndSettings")
    public void refreshUserFromLdapTest(String userName, boolean withLdapSetting) {
        LdapSetting ldapSetting = new LdapSetting();
        SearchPattern pattern = new SearchPattern();
        pattern.setSearchFilter("searchFilter");
        ldapSetting.setSearch(pattern);

        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        MutableCentralConfigDescriptor descriptor = mock(MutableCentralConfigDescriptor.class);
        SecurityDescriptor securityDescriptor = mock(SecurityDescriptor.class);
        when(centralConfigService.getMutableDescriptor()).thenReturn(descriptor);
        when(descriptor.getSecurity()).thenReturn(securityDescriptor);

        AccessService accessService = mock(AccessService.class);
        AddonsManager addonsManager = mock(AddonsManager.class);
        LdapGroupAddon ldapGroupAddon = mock(LdapGroupAddon.class);
        when(addonsManager.addonByType(LdapGroupAddon.class)).thenReturn(ldapGroupAddon);
        UserGroupService userGroupService = mock(UserGroupService.class);

        UserInfo userInfo = new UserImpl(userName);
        when(userGroupService.findOrCreateExternalAuthUser(userName, !ldapSetting.isAutoCreateUser(),
                ldapSetting.isAllowUserToAccessProfile())).thenReturn(userInfo);

        LdapService ldapService = mock(LdapService.class);
        LdapUserAttributes ldapUserAttributes = mock(LdapUserAttributes.class);
        when(ldapUserAttributes.getLdapUser()).thenReturn(new LdapUser(userName, null));
        Attributes attributes = mock(Attributes.class);
        when(ldapUserAttributes.getAttributes()).thenReturn(attributes);
        when(ldapService.getUserWithAttributesFromDomain(ldapSetting, userName)).thenReturn(ldapUserAttributes);

        if (withLdapSetting) {
            when(accessService.getLdapSettings()).thenReturn(Collections.singletonList(ldapSetting));
            SimpleUser user = LdapUtils.refreshUserFromLdap(userName, accessService, ldapService,
                    addonsManager, userGroupService);
            assertSame(user.getUsername(), userName);
        } else {
            when(accessService.getLdapSettings()).thenReturn(null);
            when(userGroupService.findUser(userName)).thenReturn(new UserImpl(userName));
            expectThrows(InternalAuthenticationServiceException.class, () -> LdapUtils.refreshUserFromLdap(userName,
                    accessService, ldapService, addonsManager, userGroupService));
        }
    }

    @DataProvider
    private static Object[][] userAndSettings() {
        return new Object[][] {
                {"arielk", true},
                {"test@jfrog.com", true},
                {"TestUser", false},
                {"uid@a124.org", false},
        };
    }
}