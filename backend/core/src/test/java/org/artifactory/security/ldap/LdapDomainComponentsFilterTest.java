package org.artifactory.security.ldap;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.springframework.ldap.core.support.LdapContextSource;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class LdapDomainComponentsFilterTest extends ArtifactoryHomeBoundTest {
    @AfterMethod
    public void afterMethod() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @DataProvider(name = "data")
    public static Object[][] data() {
        return new Object[][] {
                {"", ""},
                {"dc=one", "dc=one"},
                {"dc=one,dc=two", "dc=one,dc=two"},
                {"ou=value,dc=one,dc=two", "dc=one,dc=two"},
                {"ou=value1,ou=value2,dc=one,dc=two", "dc=one,dc=two"},
        };
    }

    @Test(dataProvider = "data")
    public void testComponentsFiltering(String input, String expected) {
        String actual = ArtifactoryLdapAuthenticator.filterDomainComponents(input);
        assertEquals(actual, expected);
    }

    @Test
    public void testSecurityContextSearchBase() {
        LdapSetting ldapSetting = new LdapSetting();
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(new ArtifactorySystemProperties());
        ldapSetting.setLdapUrl("ldap://10.70.30.11/OU=OUL1ID0,OU=RootOU2,dc=jfrog,dc=test,dc=com");
        LdapContextSource context = ArtifactoryLdapAuthenticator.createSecurityContext(ldapSetting, artifactoryHome);
        assertEquals("OU=OUL1ID0,OU=RootOU2,dc=jfrog,dc=test,dc=com", context.getBaseLdapPathAsString());
    }

    @Test
    public void testDomainSecurityContextSearchBase() {
        LdapSetting ldapSetting = new LdapSetting();
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(new ArtifactorySystemProperties());
        ldapSetting.setLdapUrl("ldap://10.70.30.11/OU=OUL1ID0,OU=RootOU2,dc=jfrog,dc=test,dc=com");
        LdapContextSource context =
                ArtifactoryLdapAuthenticator.createSecurityContextForDomain(ldapSetting, artifactoryHome);
        assertEquals("dc=jfrog,dc=test,dc=com", context.getBaseLdapPathAsString());
    }
}
