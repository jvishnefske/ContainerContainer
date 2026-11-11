package org.artifactory.ui.rest.service.artifacts.search.searchresults;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.io.FileUtils;
import org.artifactory.api.config.ExportSettingsImpl;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.search.SavedSearchResults;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.admin.importexport.ImportExportSettings;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author oleksandrl
 */
public class ExportSearchResultsServiceTest extends ArtifactoryHomeBoundTest {

    private static final String SEARCH_NAME = "my-search";

    @Mock
    private ArtifactoryRestRequest request;

    @Mock
    private RepositoryService repositoryService;

    private ExportSearchResultsService exportSearchResultsService;
    private ArtifactoryRestResponse response;
    private AutoCloseable autoCloseable;

    @BeforeMethod
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        exportSearchResultsService = new ExportSearchResultsService();
        ReflectionTestUtils.setField(exportSearchResultsService, "repoService", repositoryService);
        response = new ArtifactoryRestResponse();
        when(request.getQueryParamByKey("name")).thenReturn(SEARCH_NAME);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    public void testExecuteWhenExportPathInvalid() {
        ImportExportSettings settings = new ImportExportSettings("$path");
        when(request.getImodel()).thenReturn(settings);

        exportSearchResultsService.execute(request, response);

        assertEquals(response.getEntity(),
                String.format("{\"error\":\"%s\"}", ExportSettings.INVALID_EXPORT_DIR));
        verify(repositoryService, never()).exportSearchResults(any(), any(ExportSettingsImpl.class));
    }

    @Test
    public void testExecuteWhenPathOutsideAllowList() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.exportAllowedPaths.getPropertyName(), "/tmp/");
        try {
            ImportExportSettings settings = new ImportExportSettings("/var/export");
            when(request.getImodel()).thenReturn(settings);

            exportSearchResultsService.execute(request, response);

            assertEquals(response.getEntity(),
                    String.format("{\"error\":\"%s\"}", ExportSettings.EXPORT_PATH_NOT_IN_ALLOWLIST));
            verify(repositoryService, never()).exportSearchResults(any(), any(ExportSettingsImpl.class));
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
            ImportExportSettings settings = new ImportExportSettings("$path");
            when(request.getImodel()).thenReturn(settings);

            exportSearchResultsService.execute(request, response);

            assertEquals(response.getEntity(),
                    String.format("{\"error\":\"%s\"}", ExportSettings.INVALID_EXPORT_DIR));
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.exportAllowedPaths.getPropertyName(), "");
        }
    }

    @Test
    public void testExecuteSuccessfullyExportsWhenAllowlistNotConfigured() {
        SavedSearchResults savedSearchResults =
                new SavedSearchResults(SEARCH_NAME, Collections.emptyList());
        stubSavedSearchResultsInSession(savedSearchResults);

        ImportExportSettings settings = new ImportExportSettings("export-path");
        when(request.getImodel()).thenReturn(settings);

        exportSearchResultsService.execute(request, response);

        assertEquals(response.getEntity(), String.format("{\"info\":\"%s\"}",
                "Successfully exported '" + SEARCH_NAME + "' to 'export-path'."));
        verify(repositoryService).exportSearchResults(any(SavedSearchResults.class), any(ExportSettingsImpl.class));
    }

    @Test
    public void testExecuteSuccessfullyExportsWhenPathInsideAllowList() throws Exception {
        File allowedRoot = Files.createTempDirectory("search-export").toFile();
        try {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.exportAllowedPaths.getPropertyName(),
                            allowedRoot.getAbsolutePath());

            SavedSearchResults savedSearchResults =
                    new SavedSearchResults(SEARCH_NAME, Collections.emptyList());
            stubSavedSearchResultsInSession(savedSearchResults);

            ImportExportSettings settings = new ImportExportSettings(allowedRoot.getAbsolutePath());
            when(request.getImodel()).thenReturn(settings);

            exportSearchResultsService.execute(request, response);

            assertEquals(response.getEntity(), String.format("{\"info\":\"%s\"}",
                    "Successfully exported '" + SEARCH_NAME + "' to '" + allowedRoot.getAbsolutePath() + "'."));
            verify(repositoryService).exportSearchResults(any(SavedSearchResults.class), any(ExportSettingsImpl.class));
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.exportAllowedPaths.getPropertyName(), "");
            FileUtils.deleteQuietly(allowedRoot);
        }
    }

    private void stubSavedSearchResultsInSession(SavedSearchResults savedSearchResults) {
        HttpSession session = Mockito.mock(HttpSession.class);
        when(session.getAttribute(SEARCH_NAME)).thenReturn(savedSearchResults);

        HttpServletRequest servletRequest = Mockito.mock(HttpServletRequest.class);
        when(servletRequest.getSession(false)).thenReturn(session);
        when(request.getServletRequest()).thenReturn(servletRequest);
    }
}
