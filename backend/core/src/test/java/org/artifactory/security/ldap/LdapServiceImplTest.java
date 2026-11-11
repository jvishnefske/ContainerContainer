package org.artifactory.security.ldap;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.LdapGroupAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.ldap.LdapService;
import org.artifactory.api.security.ldap.LdapUser;
import org.artifactory.api.security.ldap.LdapUserAttributes;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.SearchPattern;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.security.ldap.search.LdapUserSearch;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * @author BarakH
 */
public class LdapServiceImplTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ArtifactoryApplicationContext applicationContext;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private LdapGroupAddon ldapGroupAddon;
    @Mock
    private AolService aolService;

    private AutoCloseable mocks;
    private LdapService ldapService;

    @BeforeMethod
    public void beforeMethod() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(applicationContext);
        when(applicationContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(applicationContext.beanForType(AolService.class)).thenReturn(aolService);
        when(addonsManager.addonByType(LdapGroupAddon.class)).thenReturn(ldapGroupAddon);

        ldapService = new LdapServiceImpl();
    }


    @AfterMethod
    public void afterMethod() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testGetAccountLockoutDurationCached() {
        LdapServiceTestChild ldapService = new LdapServiceTestChild();
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setLdapUrl("ldap://10.70.30.11/OU=OUL1ID0,OU=RootOU2,dc=jfrog,dc=test,dc=com");

        ldapService.lockoutDurationAttributeValue = 5L;
        assertEquals(Long.valueOf(5), ldapService.getAccountLockoutDuration(ldapSetting));

        ldapService.lockoutDurationAttributeValue = 10L;
        assertEquals(Long.valueOf(5), ldapService.getAccountLockoutDuration(ldapSetting));
    }

    @Test
    public void testGetUserWithAttributesFromDomainDisabledLdapSettingReturnNull() {
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setEnabled(false);
        LdapUserAttributes ldapUserAttributes =
                ldapService.getUserWithAttributesFromDomain(ldapSetting, "username");

        assertNull(ldapUserAttributes);
    }

    @Test
    public void testGetUserWithAttributesFromDomainNullLdapSettingReturnNull() {
        LdapUserAttributes ldapUserAttributes =
                ldapService.getUserWithAttributesFromDomain(new LdapSetting(), "username");

        assertNull(ldapUserAttributes);
    }

    @Test
    public void testGetUserWithAttributesFromDomainNullSearchReturnNull() {
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setSearch(null);
        LdapUserAttributes ldapUserAttributes =
                ldapService.getUserWithAttributesFromDomain(ldapSetting, "username");

        assertNull(ldapUserAttributes);
    }

    @Test
    public void testGetUserWithAttributesFromDomainNullSearchFilterReturnNull() {
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setSearch(new SearchPattern());
        LdapUserAttributes ldapUserAttributes =
                ldapService.getUserWithAttributesFromDomain(ldapSetting, "username");

        assertNull(ldapUserAttributes);
    }

    @Test
    public void testGetUserWithAttributesFromDomainBlankSearchFilterReturnNull() {
        LdapSetting ldapSetting = new LdapSetting();
        SearchPattern searchPattern = new SearchPattern();
        searchPattern.setSearchFilter(StringUtils.EMPTY);
        ldapSetting.setSearch(searchPattern);
        LdapUserAttributes ldapUserAttributes =
                ldapService.getUserWithAttributesFromDomain(ldapSetting, "username");

        assertNull(ldapUserAttributes);
    }

    @Test
    public void testGetUserWithAttributesFromDomainUserFound() {
        String username = "username";

        DirContextAdapter dirContextAdapter = mock(DirContextAdapter.class);
        when(dirContextAdapter.getNameInNamespace()).thenReturn(username);
        LdapUserSearch ldapUserSearch = mock(LdapUserSearch.class);
        when(ldapUserSearch.searchForUser(username)).thenReturn(dirContextAdapter);
        when(ldapGroupAddon.getLdapUserSearches(any(), any(), anyBoolean())).thenReturn(List.of(ldapUserSearch));

        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setLdapUrl("ldap://host:555/dn=InternalUsers");
        SearchPattern searchPattern = new SearchPattern();
        searchPattern.setSearchFilter("searchFilter");
        ldapSetting.setSearch(searchPattern);

        LdapUserAttributes ldapUserAttributes = ldapService.getUserWithAttributesFromDomain(ldapSetting, username);

        assertNotNull(ldapUserAttributes);
        LdapUser expectedLdapUser = new LdapUser("username", null);
        assertEquals(ldapUserAttributes.getLdapUser(), expectedLdapUser);
        assertNull(ldapUserAttributes.getAttributes());
    }

    @Test
    public void testGetUserWithAttributesFromDomainUserNotFound() {
        String username = "username";

        LdapUserSearch ldapUserSearch = mock(LdapUserSearch.class);
        when(ldapUserSearch.searchForUser(username)).thenReturn(null);
        when(ldapGroupAddon.getLdapUserSearches(any(), any(), anyBoolean())).thenReturn(List.of(ldapUserSearch));

        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setLdapUrl("ldap://host:555/dn=InternalUsers");
        SearchPattern searchPattern = new SearchPattern();
        searchPattern.setSearchFilter("searchFilter");
        ldapSetting.setSearch(searchPattern);

        LdapUserAttributes ldapUserAttributes = ldapService.getUserWithAttributesFromDomain(ldapSetting, username);

        assertNull(ldapUserAttributes);
    }

    private static class LdapServiceTestChild extends LdapServiceImpl {

        private Long lockoutDurationAttributeValue;

        @Override
        Long loadLockoutDurationAttributeValue(LdapSetting setting) {
            return lockoutDurationAttributeValue;
        }
    }
}