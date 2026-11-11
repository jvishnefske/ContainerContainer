package org.artifactory.rest.resource.system;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.exception.ForbiddenWebAppException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertTrue;

public class SystemResourceTest {
    @Mock
    private ArtifactoryHome artifactoryHome;
    private SystemResource systemResource;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private AolService aolService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private PluginsAddon pluginsAddon;
    @Mock
    private ArtifactorySystemProperties systemProperties;
    private AutoCloseable autoCloseable;


    @BeforeMethod
    private void setup() {
        this.systemResource = new SystemResource();
        this.autoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryHome.bind(this.artifactoryHome);
        ArtifactoryContextThreadBinder.bind(this.context);
    }

    @AfterMethod
    private void cleanup() throws Exception {
        this.autoCloseable.close();
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPluginSystemInfoNotShowingInAol() throws Exception {
        this.createMockForPluginInfo(true, false);
        this.systemResource.getPluginSystemInfo();
    }

    @Test(dataProvider = "configure")
    public void testPluginSystemInfoShowingInAolDashboardUser(boolean isAol, boolean isDashboardUser)
            throws Exception {
        this.createMockForPluginInfo(isAol, isDashboardUser);
        assertTrue(this.systemResource.getPluginSystemInfo().toLowerCase().contains("system info"));
    }

    @Test(expectedExceptions = ForbiddenWebAppException.class)
    public void testVerifyConnectionEndpointNotAllowedInSaaS() throws Exception {
        ArtifactoryContextThreadBinder.bind(context);
        VerifyConnectionModel verifyConnectionModel = new VerifyConnectionModel();
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        when(aolService.isAol()).thenReturn(true);
        systemResource.verifyConnection(verifyConnectionModel);
    }

    @DataProvider
    public Object[][] configure() {
        return new Object[][] {
                {true, true},
                {false, false}
        };
    }

    private void createMockForPluginInfo(boolean isAol, boolean isDashboardUser) {
        Properties props = new Properties();
        props.put("bla", "bla");
        when(this.aolService.isDashboardUser()).thenReturn(isDashboardUser);
        when(this.aolService.isAol()).thenReturn(isAol);
        when(this.context.beanForType(AolService.class)).thenReturn(this.aolService);
        when(this.context.beanForType(AddonsManager.class)).thenReturn(this.addonsManager);
        when(this.addonsManager.addonByType(any())).thenReturn(this.pluginsAddon);
        when(this.pluginsAddon.getPluginsInfoSupportBundleDump()).thenReturn("bla");
        when(this.artifactoryHome.getArtifactoryProperties()).thenReturn(this.systemProperties);
        when(this.systemProperties.getPropertiesCopy()).thenReturn(props);
    }
}
