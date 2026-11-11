package org.artifactory.ui.rest.service.admin.importexport.exportdata;

import org.artifactory.api.config.ExportSettingsImpl;
import org.artifactory.api.repo.BackupService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.admin.importexport.ImportExportSettings;
import org.artifactory.util.AolUtils;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author oleksandrl
 */
public class ExportRepositoryServiceTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ArtifactoryRestRequest request;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private BackupService backupService;

    private ExportRepositoryService exportRepositoryService;
    private ArtifactoryRestResponse response;
    private AutoCloseable autoCloseable;

    @BeforeMethod
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        exportRepositoryService = new ExportRepositoryService();
        ReflectionTestUtils.setField(exportRepositoryService, "repositoryService", repositoryService);
        ReflectionTestUtils.setField(exportRepositoryService, "backupService", backupService);
        response = new ArtifactoryRestResponse();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    public void testExecuteWhenExportPathInvalid() {
        ImportExportSettings importExportSettings = new ImportExportSettings("$path");
        importExportSettings.setExcludeContent(true);
        when(request.getImodel()).thenReturn(importExportSettings);

        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(false);
            exportRepositoryService.execute(request, response);
        }
        assertEquals(response.getEntity(),
                String.format("{\"error\":\"%s\"}", ExportSettings.INVALID_EXPORT_DIR));
        verify(repositoryService, never()).exportRepo(anyString(), any(ExportSettingsImpl.class));
        verify(backupService, never()).backupRepos(any(), any(ExportSettingsImpl.class));
    }

    @Test
    public void testExecuteWhenPathOutsideAllowList() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.exportAllowedPaths.getPropertyName(), "/tmp/");
        try {
            ImportExportSettings importExportSettings = new ImportExportSettings("/var/export");
            importExportSettings.setExcludeContent(true);
            when(request.getImodel()).thenReturn(importExportSettings);

            try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
                mockedAolUtils.when(AolUtils::isAol).thenReturn(false);
                exportRepositoryService.execute(request, response);
            }
            assertEquals(response.getEntity(),
                    String.format("{\"error\":\"%s\"}", ExportSettings.EXPORT_PATH_NOT_IN_ALLOWLIST));
            verify(repositoryService, never()).exportRepo(anyString(), any(ExportSettingsImpl.class));
            verify(backupService, never()).backupRepos(any(), any(ExportSettingsImpl.class));
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.exportAllowedPaths.getPropertyName(), "");
        }
    }

    @Test
    public void testExecuteWhenInvalidPathTakesPrecedenceOverAllowlist() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.exportAllowedPaths.getPropertyName(), "/tmp/");
        try {
            ImportExportSettings importExportSettings = new ImportExportSettings("$path");
            importExportSettings.setExcludeContent(true);
            when(request.getImodel()).thenReturn(importExportSettings);

            try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
                mockedAolUtils.when(AolUtils::isAol).thenReturn(false);
                exportRepositoryService.execute(request, response);
            }
            assertEquals(response.getEntity(),
                    String.format("{\"error\":\"%s\"}", ExportSettings.INVALID_EXPORT_DIR));
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.exportAllowedPaths.getPropertyName(), "");
        }
    }
}
