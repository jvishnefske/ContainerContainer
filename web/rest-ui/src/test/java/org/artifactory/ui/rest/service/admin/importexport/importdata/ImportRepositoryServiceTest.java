package org.artifactory.ui.rest.service.admin.importexport.importdata;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.admin.importexport.ImportExportSettings;
import org.artifactory.util.AolUtils;
import org.artifactory.util.Files;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Yevdo Abramov
 * Created on 04/02/2020
 */
public class ImportRepositoryServiceTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ArtifactoryApplicationContext applicationContext;

    @Mock
    private ArtifactoryRestRequest request;

    private ArtifactoryRestResponse response;

    private ImportRepositoryService importRepositoryService;

    private AutoCloseable autoCloseable;

    @BeforeMethod
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        response = new ArtifactoryRestResponse();
        RepositoryService repositoryService = Mockito.mock(RepositoryService.class);
        importRepositoryService = new ImportRepositoryService(repositoryService);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test(testName = "test execute when repositoryImportEnabled is false")
    public void testExecuteWhenRepositoryImportDisabled() {
        repositoryImportEnable(false);

        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(false);
            importRepositoryService.execute(request, response);
        }
        assertEquals(response.getEntity(), "{\"error\":\"Repositories Import is disabled.\"}");
        assertEquals(response.getResponseCode(), HttpStatus.SC_FORBIDDEN);
    }

    @Test
    public void testExecuteWhenRepositoryPathInvalid() {
        repositoryImportEnable(true);

        ImportExportSettings importExportSettings = new ImportExportSettings("$path");
        when(request.getImodel()).thenReturn(importExportSettings);

        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(false);
            importRepositoryService.execute(request, response);
        }
        assertEquals(response.getEntity(), String.format("{\"error\":\"%s\"}", ImportSettings.INVALID_IMPORT_DIR));
    }

    @Test
    public void testExecuteWhenPathOutsideAllowList() {
        repositoryImportEnable(true);
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.importAllowedPaths.getPropertyName(), "/tmp/");
        try {
            // path is structurally valid (passes the legacy check) but is not inside the allowlist
            ImportExportSettings importExportSettings = new ImportExportSettings("/var/import");
            when(request.getImodel()).thenReturn(importExportSettings);

            try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
                mockedAolUtils.when(AolUtils::isAol).thenReturn(false);
                importRepositoryService.execute(request, response);
            }
            assertEquals(response.getEntity(),
                    String.format("{\"error\":\"%s\"}", ImportSettings.IMPORT_PATH_NOT_IN_ALLOWLIST));
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.importAllowedPaths.getPropertyName(), "");
        }
    }

    @Test(dataProvider = "booleanProvider")
    public void testHomePathIsValidOnlyForZip(boolean isZip) {
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(false);
            ArtifactoryContextThreadBinder.bind(applicationContext);
            repositoryImportEnable(true);

            String artifactoryHomePath = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsolutePath();
            ImportExportSettings importExportSettings = new ImportExportSettings(artifactoryHomePath + "/path");
            importExportSettings.setRepository("random_repo");
            importExportSettings.setZip(isZip);
            when(request.getImodel()).thenReturn(importExportSettings);

            when(applicationContext.getArtifactoryHome()).thenReturn(getOrCreateArtifactoryHomeStub());
            importRepositoryService.execute(request, response);
            if (isZip) {
                assertEquals(response.getEntity(), String.format("{\"error\":\"%s\"}",
                        "Unable to locate the uploaded content."));
            } else {
                assertEquals(response.getEntity(), String.format("{\"error\":\"%s\"}",
                        ImportSettings.INVALID_IMPORT_DIR));
            }
        }
    }

    @Test
    public void testExecuteWhenUnableToLocateTheUploadedContent() {
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(true);
            ArtifactoryContextThreadBinder.bind(applicationContext);
            repositoryImportEnable(true);

            ImportExportSettings importExportSettings = new ImportExportSettings("path");
            importExportSettings.setZip(true);
            when(request.getImodel()).thenReturn(importExportSettings);
            when(applicationContext.getArtifactoryHome()).thenReturn(getOrCreateArtifactoryHomeStub());

            importRepositoryService.execute(request, response);
            assertEquals(response.getEntity(), String.format("{\"error\":\"%s\"}",
                    "Unable to locate the uploaded content."));
        }
    }

    @Test
    public void testExecuteWhenAolAndNotZip() {
        repositoryImportEnable(true);

        ImportExportSettings importExportSettings = new ImportExportSettings("some-path");
        importExportSettings.setZip(false);
        when(request.getImodel()).thenReturn(importExportSettings);

        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(true);
            importRepositoryService.execute(request, response);
        }
        assertEquals(response.getEntity(), String.format("{\"error\":\"%s\"}",
                ImportSettings.SAAS_IMPORT_ZIP_ONLY_ALLOWED_ERROR));
    }

    @Test(dataProvider = "validationErrorProvider")
    public void testGetValidationErrorIfAny(String path, boolean isZip, boolean isAol, String expectedError)
            throws Exception {
        if (ImportSettings.IMPORT_PATH_NOT_IN_ALLOWLIST.equals(expectedError)) {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.importAllowedPaths.getPropertyName(), "/tmp/");
        }
        try {
            ImportExportSettings importExportSettings = new ImportExportSettings(path);
            importExportSettings.setZip(isZip);

            Optional<String> validationError = invokeGetValidationErrorIfAny(importExportSettings, isAol);

            if (expectedError == null) {
                assertTrue(validationError.isEmpty());
            } else {
                assertEquals(validationError, Optional.of(expectedError));
            }
        } finally {
            if (ImportSettings.IMPORT_PATH_NOT_IN_ALLOWLIST.equals(expectedError)) {
                ArtifactoryHome.get().getArtifactoryProperties()
                        .setProperty(ConstantValues.importAllowedPaths.getPropertyName(), "");
            }
        }
    }

    @Test
    public void testHandleFailureDeletesExtractedZipOnAol() throws Exception {
        ImportExportSettings importExportSettings = new ImportExportSettings("my-uuid");
        importExportSettings.setZip(true);

        try (MockedStatic<FileUtils> fileUtilsMock = Mockito.mockStatic(FileUtils.class)) {
            invokeHandleFailure("/tmp/upload", "my-uuid", true, importExportSettings);
            fileUtilsMock.verify(() -> FileUtils.deleteQuietly(any(File.class)), times(1));
        }
    }

    @Test
    public void testHandleFailureRemovesFileOnNonAolZipImport() throws Exception {
        ImportExportSettings importExportSettings = new ImportExportSettings("/data/import.zip");
        importExportSettings.setZip(true);

        try (MockedStatic<Files> filesMock = Mockito.mockStatic(Files.class)) {
            invokeHandleFailure("/tmp/upload", "my-uuid", false, importExportSettings);
            filesMock.verify(() -> Files.removeFile(new File("/data/import.zip")), times(1));
        }
    }

    @Test
    public void testHandleFailureDoesNothingWhenNotZip() throws Exception {
        ImportExportSettings importExportSettings = new ImportExportSettings("/data/import");
        importExportSettings.setZip(false);

        try (MockedStatic<FileUtils> fileUtilsMock = Mockito.mockStatic(FileUtils.class);
                MockedStatic<Files> filesMock = Mockito.mockStatic(Files.class)) {
            invokeHandleFailure("/tmp/upload", "/data/import", false, importExportSettings);
            fileUtilsMock.verifyNoInteractions();
            filesMock.verifyNoInteractions();
        }
    }

    @Test
    public void testExecuteAndSuccessfullyImported() {
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(false);
            ArtifactoryContextThreadBinder.bind(applicationContext);
            repositoryImportEnable(true);

            ImportExportSettings importExportSettings = new ImportExportSettings("path");
            importExportSettings.setRepository("random_repo");
            when(request.getImodel()).thenReturn(importExportSettings);
            when(applicationContext.getArtifactoryHome()).thenReturn(getOrCreateArtifactoryHomeStub());

            importRepositoryService.execute(request, response);
            assertEquals(response.getEntity(), String.format("{\"info\":\"%s\"}",
                    "Successfully imported 'path' into 'random_repo'."));
        }
    }

    private void repositoryImportEnable(boolean enable) {
        ArtifactoryHome.get()
                .getArtifactoryProperties()
                .setProperty(ConstantValues.repositoryImportEnabled.getPropertyName(),
                        Boolean.toString(enable));

    }

    @DataProvider(name = "booleanProvider")
    public Object[][] provideBooleanData() {
        return new Object[][] {
                {true},
                {false},
        };
    }

    @DataProvider(name = "validationErrorProvider")
    public Object[][] validationErrorProvider() {
        return new Object[][] {
                {"$invalid", false, true, ImportSettings.INVALID_IMPORT_DIR},
                {"/var/import", false, false, ImportSettings.IMPORT_PATH_NOT_IN_ALLOWLIST},
                {"some-path", false, true, ImportSettings.SAAS_IMPORT_ZIP_ONLY_ALLOWED_ERROR},
                {"some-path", false, false, null},
                {"some-path", true, true, null},
        };
    }

    private Optional<String> invokeGetValidationErrorIfAny(ImportExportSettings importExportSettings, boolean isAol)
            throws Exception {
        Method method = ImportRepositoryService.class.getDeclaredMethod(
                "getValidationErrorIfAny", ImportExportSettings.class, boolean.class);
        method.setAccessible(true);
        return (Optional<String>) method.invoke(importRepositoryService, importExportSettings, isAol);
    }

    private void invokeHandleFailure(String uploadDir, String filePath, boolean isAol,
            ImportExportSettings importExportSettings) throws Exception {
        Method method = ImportRepositoryService.class.getDeclaredMethod(
                "handleFailure", String.class, String.class, boolean.class, ImportExportSettings.class);
        method.setAccessible(true);
        method.invoke(importRepositoryService, uploadDir, filePath, isAol, importExportSettings);
    }
}
