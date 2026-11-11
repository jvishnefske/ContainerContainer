package org.artifactory.addon.filteredresources;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.request.Request;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.InputStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
/**
 * @author Natalie Berni
 */
public class FilteredResourcesAddonTest extends ArtifactoryHomeBoundTest {

    @AfterMethod
    public void tearDown() {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }

    private ArtifactoryContext context;
    private FilteredResourcesAddon filteredResourcesAddon;


    @BeforeMethod
    public void setUp() {
        filteredResourcesAddon = new FilteredResourcesAddon() {
            @Override
            public boolean isDefault() {
                return false;
            }

            @Override
            public RepoResource getFilteredResource(Request request, FileInfo fileInfo, InputStream fileInputStream) {
                return null;
            }

            @Override
            public RepoResource getZipResource(Request request, FileInfo info, InputStream stream) {
                return null;
            }
        };

        context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @Test
    public void getGeneratedSettingsUserCredentialsTemplate_returnsEmptyStringForAnonymousUser() {
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnonymous()).thenReturn(true);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);

        String result = filteredResourcesAddon.getGeneratedSettingsUserCredentialsTemplate(true);
        assertEquals(result, "");
    }

    @Test
    public void getGeneratedSettingsUserCredentialsTemplate_returnsEscapedEncryptedPassword() {
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnonymous()).thenReturn(false);
        when(securityService.isTransientUser()).thenReturn(false);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);

        String result = filteredResourcesAddon.getGeneratedSettingsUserCredentialsTemplate(true);
        assertEquals(result,
                "${security.getEscapedEncryptedPassword()!\"*** Insert encrypted password here ***\"}");

    }
    @Test
    public void getGeneratedSettingsUserCredentialsTemplate_defaultBehaviorWithoutForcing() {
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnonymous()).thenReturn(true);
        when(securityService.isTransientUser()).thenReturn(true);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);

        String result = filteredResourcesAddon.getGeneratedSettingsUserCredentialsTemplate(true, true);
        assertEquals(result,
                "${security.getEscapedEncryptedPassword()!\"*** Insert encrypted password here ***\"}");

    }

    @Test
    public void getGeneratedSettingsUserCredentialsTemplate_isTransientUser() {
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnonymous()).thenReturn(false);
        when(securityService.isTransientUser()).thenReturn(true);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);

        String result = filteredResourcesAddon.getGeneratedSettingsUserCredentialsTemplate(true);
        assertEquals(result, "");
    }

    @Test
    public void getGeneratedSettingsUserCredentialsTemplate_returnsEncryptedPassword() {
        when(context.beanForType(SecurityService.class)).thenReturn(mock(SecurityService.class));
        String result =
                filteredResourcesAddon.getGeneratedSettingsUserCredentialsTemplate(false);

        assertEquals(result, "${security.getEncryptedPassword()!\"*** Insert encrypted password here ***\"}");
    }

    @Test
    public void getGeneratedSettingsUserCredentialsTemplate_forceGenerateReturnsEncryptedPasswordForAnonymousUser() {
        when(context.beanForType(SecurityService.class)).thenReturn(mock(SecurityService.class));
        String result =
                filteredResourcesAddon.getGeneratedSettingsUserCredentialsTemplate(false, true);
        assertEquals(result, "${security.getEncryptedPassword()!\"*** Insert encrypted password here ***\"}");
    }
}