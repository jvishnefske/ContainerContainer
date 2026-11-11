package org.artifactory.rest.resource.system;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import java.util.List;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * @author Andrey Tashlitsky
 */
class ExportResourceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext context;
    @Mock
    private AolService aolService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ArtifactRetentionAddon artifactRetentionAddon;
    private AutoCloseable autoCloseable;

    @BeforeEach
    public void beforeEach() {
        autoCloseable = openMocks(this);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        when(addonsManager.addonByType(ArtifactRetentionAddon.class)).thenReturn(artifactRetentionAddon);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void afterEach() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void testActivateImport() {
        ExportSettingsConfigurationImpl settings = new ExportSettingsConfigurationImpl();
        settings.setExportPath("path");
        ExportResource exportResource = new ExportResource();
        HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);
        when(httpServletResponse.isCommitted()).thenReturn(true);
        exportResource.httpResponse = httpServletResponse;
        setField(exportResource, "repoService", repositoryService);
        when(repositoryService.getLocalFederatedAndCachedRepoConfigs()).thenReturn(List.of());
        try (Response response = exportResource.activateExport(settings)) {
            assertEquals(SC_OK, response.getStatus());
        }
    }

    @Test
    void testActivateImportHomePath() {
        ExportSettingsConfigurationImpl settings = new ExportSettingsConfigurationImpl();
        settings.setExportPath(getOrCreateArtifactoryHomeStub().getHomeDir().getAbsolutePath());
        ExportResource exportResource = new ExportResource();
        try (Response response = exportResource.activateExport(settings)) {
            assertEquals(SC_BAD_REQUEST, response.getStatus());
        }
    }
}
