package org.artifactory.security.ldap;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.api.security.ldap.LdapService;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.access.model.Realm;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.UncategorizedLdapException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Andrey Tashlitsky
 */
public class ArtifactoryLdapAuthenticationProviderTest extends ArtifactoryHomeBoundTest {

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private LdapService ldapService;
    @Mock
    private InternalLdapAuthenticator internalLdapAuthenticator;
    @Mock
    private UserGroupService userGroupService;
    @Mock
    private AccessService accessService;

    private AutoCloseable autoCloseable;

    @BeforeClass
    public void beforeClass() {
        autoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterClass
    public void afterClass() throws Exception {
        autoCloseable.close();
    }

    @Test(dataProvider = "testShouldRemoveUserLdapRelatedGroupsDataProvider")
    public void testShouldRemoveUserLdapRelatedGroups(RuntimeException e, boolean expectedResult) {
        ArtifactoryLdapAuthenticationProvider artifactoryLdapAuthenticationProvider =
                new ArtifactoryLdapAuthenticationProvider(addonsManager, ldapService, internalLdapAuthenticator,
                        userGroupService, accessService);
        assertEquals(artifactoryLdapAuthenticationProvider.shouldRemoveUserLdapRelatedGroups(e), expectedResult);
    }

    @DataProvider
    private Object[][] testShouldRemoveUserLdapRelatedGroupsDataProvider() {
        UncategorizedLdapException uncategorizedLdapException = mock(UncategorizedLdapException.class);
        UncategorizedLdapException uncategorizedLdapExceptionCause = mock(UncategorizedLdapException.class);
        doReturn(uncategorizedLdapExceptionCause).when(uncategorizedLdapException).getCause();
        doReturn("LDAP response read timed out").when(uncategorizedLdapExceptionCause).getMessage();
        UncategorizedLdapException uncategorizedLdapExceptionNullCause = mock(UncategorizedLdapException.class);
        doReturn(null).when(uncategorizedLdapExceptionNullCause).getCause();
        UncategorizedLdapException uncategorizedLdapExceptionNullMsg = mock(UncategorizedLdapException.class);
        UncategorizedLdapException uncategorizedLdapExceptionSomeCause = mock(UncategorizedLdapException.class);
        doReturn(uncategorizedLdapExceptionSomeCause).when(uncategorizedLdapExceptionNullMsg).getCause();
        doReturn(null).when(uncategorizedLdapExceptionSomeCause).getMessage();
        return new Object[][] {
                {mock(BadCredentialsException.class), false},
                {mock(CommunicationException.class), false},
                {uncategorizedLdapException, false},
                {uncategorizedLdapExceptionNullCause, true},
                {uncategorizedLdapExceptionNullMsg, true},
                {mock(RuntimeException.class), true},
        };
    }

    @Test
    public void testSupportsInverseToAccessLdapRealm() {
        when(accessService.isRealmAuthenticationEnabled(Realm.LDAP)).thenReturn(true);

        ArtifactoryLdapAuthenticationProvider artifactoryLdapAuthenticationProvider =
                new ArtifactoryLdapAuthenticationProvider(addonsManager, ldapService, internalLdapAuthenticator,
                        userGroupService, accessService);

        Assert.assertFalse(artifactoryLdapAuthenticationProvider.supports(Authentication.class));
    }
}
