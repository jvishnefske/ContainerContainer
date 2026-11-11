package org.artifactory.ui.rest.service.admin.importexport.exportdata;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.ExportSettingsImpl;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.admin.importexport.ImportExportSettings;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Alexei Vainshtein
 */
public class ExportSystemServiceTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ArtifactoryApplicationContext applicationContext;

    private AutoCloseable autoCloseable;

    private ExportSystemService exportSystemService;

    @BeforeMethod
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(applicationContext);
        RepositoryService repositoryService = Mockito.mock(RepositoryService.class);
        RepositoryConfigService repositoryConfigService = Mockito.mock(RepositoryConfigService.class);
        exportSystemService = new ExportSystemService(repositoryService, repositoryConfigService);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test(dataProvider = "booleanProvider")
    public void testUpdateExportSettings(boolean shouldActOnMissionControl) {
        ImportExportSettings importExportSettings = new ImportExportSettings("path");
        importExportSettings.setExcludeContent(true);
        importExportSettings.setExcludeMetadata(true);
        importExportSettings.setActOnMissionControl(shouldActOnMissionControl);
        ExportSettings exportSettings = new ExportSettingsImpl(null);
        exportSystemService.updateExportSettings(importExportSettings, exportSettings);
        assertEquals(exportSettings.shouldActOnMissionControl(), shouldActOnMissionControl);
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testExecuteForArtifactorySaasShouldReturnError() {
        ArtifactoryRestRequest request = Mockito.mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        AolService aolService = Mockito.mock(AolService.class);
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);

        when(applicationContext.beanForType(AolService.class)).thenReturn(aolService);
        exportSystemService.execute(request, response);

        assertEquals(response.getEntity(),
                String.format("{\"error\":\"%s\"}", "Artifactory SaaS does not support this feature."));
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testExecuteInvalidPath() {
        ArtifactoryRestRequest request = Mockito.mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        AolService aolService = Mockito.mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);

        when(applicationContext.beanForType(AolService.class)).thenReturn(aolService);

        ImportExportSettings importExportSettings = new ImportExportSettings("$path");
        when(request.getImodel()).thenReturn(importExportSettings);
        exportSystemService.execute(request, response);

        assertEquals(response.getEntity(), String.format("{\"error\":\"%s\"}",
                ExportSettings.INVALID_EXPORT_DIR));
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testExecuteWhenPathOutsideAllowList() {
        ArtifactoryRestRequest request = Mockito.mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        AolService aolService = Mockito.mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);
        when(applicationContext.beanForType(AolService.class)).thenReturn(aolService);

        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.exportAllowedPaths.getPropertyName(), "/tmp/");
        try {
            // path is structurally valid (passes the legacy check) but is not inside the allowlist
            ImportExportSettings importExportSettings = new ImportExportSettings("/var/export");
            when(request.getImodel()).thenReturn(importExportSettings);
            exportSystemService.execute(request, response);

            assertEquals(response.getEntity(),
                    String.format("{\"error\":\"%s\"}", ExportSettings.EXPORT_PATH_NOT_IN_ALLOWLIST));
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.exportAllowedPaths.getPropertyName(), "");
        }
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testExecuteFailToExportSystemToNotExistedPath() {
        ArtifactoryRestRequest request = Mockito.mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        AolService aolService = Mockito.mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);

        when(applicationContext.beanForType(AolService.class)).thenReturn(aolService);
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        when(applicationContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        ArtifactRetentionAddon artifactRetentionAddon = Mockito.mock(ArtifactRetentionAddon.class);
        when(addonsManager.addonByType(ArtifactRetentionAddon.class)).thenReturn(artifactRetentionAddon);
        doCallRealMethod().when(applicationContext).exportTo(any());

        ImportExportSettings importExportSettings = new ImportExportSettings("path");
        when(request.getImodel()).thenReturn(importExportSettings);
        exportSystemService.execute(request, response);

        assertEquals(response.getEntity(), String.format("{\"error\":\"%s\"}",
                "Failed to export system to 'path': null"));
    }

    @DataProvider(name = "booleanProvider")
    public Object[][] provideBooleanData() {
        return new Object[][] {
                {true}, {false}
        };
    }

}