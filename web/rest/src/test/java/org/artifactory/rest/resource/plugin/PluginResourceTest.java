package org.artifactory.rest.resource.plugin;

import com.jfrog.build.acl.bridge.BuildFacade;
import jakarta.ws.rs.core.Response;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.aol.AolService;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Tamir Hadad
 */
class PluginResourceTest {

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private BuildFacade buildFacade;
    @Mock
    private AolService aolService;
    @Mock
    private HaAddon haAddon;
    @Mock
    private RestAddon restAddon;
    @Mock
    private ResponseCtx responseCtx;
    @Mock
    private PluginsAddon pluginsAddon;

    private PluginsResource pluginsResource;
    private AutoCloseable mocks;

    @BeforeEach
    public void init() {
        mocks = openMocks(this);
        this.pluginsResource = new PluginsResource(buildFacade, addonsManager, aolService);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testAolNotDashboardUser() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.isHaAuthentication()).thenReturn(false);
        Response response = pluginsResource.reload(true);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void testAolDashboardUser() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(true);
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        when(restAddon.reloadPlugins(anyBoolean())).thenReturn(responseCtx);
        when(responseCtx.getStatus()).thenReturn(HttpStatus.OK.value());
        when(responseCtx.getMessage()).thenReturn("tests");
        Response response = pluginsResource.reload(true);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void testAolHaUser() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.isHaAuthentication()).thenReturn(true);
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        when(restAddon.reloadPlugins(anyBoolean())).thenReturn(responseCtx);
        when(responseCtx.getStatus()).thenReturn(HttpStatus.OK.value());
        when(responseCtx.getMessage()).thenReturn("tests");
        Response response = pluginsResource.reload(true);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void testNotAol() {
        when(aolService.isAol()).thenReturn(false);
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        when(restAddon.reloadPlugins(anyBoolean())).thenReturn(responseCtx);
        when(responseCtx.getStatus()).thenReturn(HttpStatus.OK.value());
        when(responseCtx.getMessage()).thenReturn("tests");
        Response response = pluginsResource.reload(true);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void testGetPluginStatus() {
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        when(restAddon.getPluginStatus()).thenReturn(responseCtx);
        when(responseCtx.getStatus()).thenReturn(HttpStatus.OK.value());
        Response response = pluginsResource.getPluginStatus();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void testDeployPluginForbiddenWhenRemoteDeployDisabled() {
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        when(restAddon.deployPlugin(any(), anyString()))
                .thenThrow(new AuthorizationRestException("Remote plugin deployment is disabled."));

        StringReader pluginContent = new StringReader("executions {}");
        AuthorizationRestException exception = assertThrows(AuthorizationRestException.class,
                () -> pluginsResource.deploy(pluginContent, "blocked.groovy"));
        assertThat(exception.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void testDeletePluginSuccess() {
        String pluginName = "testPlugin";
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        when(restAddon.deletePlugin(anyString())).thenReturn(responseCtx);
        when(responseCtx.getStatus()).thenReturn(HttpStatus.OK.value());
        when(responseCtx.getMessage()).thenReturn("Plugin deleted successfully: " + pluginName);

        Response response = pluginsResource.deletePlugin(pluginName);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void testDeletePluginNotFound() {
        String pluginName = "nonExistentPlugin.groovy";
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        when(restAddon.deletePlugin(anyString())).thenReturn(responseCtx);
        when(responseCtx.getStatus()).thenReturn(HttpStatus.NOT_FOUND.value());
        when(responseCtx.getMessage()).thenReturn("Plugin not found: " + pluginName + ".groovy");

        Response response = pluginsResource.deletePlugin(pluginName);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void testDeletePluginInternalServerError() {
        String pluginName = "problematicPlugin";
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        when(restAddon.deletePlugin(anyString())).thenReturn(responseCtx);
        when(responseCtx.getStatus()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR.value());
        when(responseCtx.getMessage()).thenReturn("Failed to delete plugin: " + pluginName);

        Response response = pluginsResource.deletePlugin(pluginName);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
}
