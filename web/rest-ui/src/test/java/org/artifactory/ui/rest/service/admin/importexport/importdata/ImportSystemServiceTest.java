package org.artifactory.ui.rest.service.admin.importexport.importdata;

import org.apache.http.HttpStatus;
import org.artifactory.api.common.ImportExportStatusHolder;
import org.artifactory.api.config.ImportSettingsImpl;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.admin.importexport.ImportExportSettings;
import org.artifactory.util.AolUtils;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.assertEquals;

/**
 * @author Yevdo Abramov
 * Created on 04/02/2020
 */
public class ImportSystemServiceTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ArtifactoryRestRequest request;

    private ArtifactoryRestResponse response;

    private ImportSystemService importSystemService;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        mocks = openMocks(this);
        response = new ArtifactoryRestResponse();
        importSystemService = new ImportSystemService();
        ArtifactoryHome.get()
                .getArtifactoryProperties()
                .setProperty(ConstantValues.systemImportEnabled.getPropertyName(), Boolean.toString(true));
    }

    @Test
    public void testExecuteWhenImportPathInvalid() {
        ImportExportSettings importExportSettings = new ImportExportSettings("$path");
        when(request.getImodel()).thenReturn(importExportSettings);

        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(false);
            importSystemService.execute(request, response);
        }
        assertEquals(response.getEntity(),
                String.format("{\"error\":\"%s\"}", ImportSettings.INVALID_IMPORT_DIR));
    }

    @Test
    public void testExecuteWhenPathOutsideAllowList() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.importAllowedPaths.getPropertyName(), "/tmp/");
        try {
            ImportExportSettings importExportSettings = new ImportExportSettings("/var/import");
            when(request.getImodel()).thenReturn(importExportSettings);

            try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
                mockedAolUtils.when(AolUtils::isAol).thenReturn(false);
                importSystemService.execute(request, response);
            }
            assertEquals(response.getEntity(),
                    String.format("{\"error\":\"%s\"}", ImportSettings.IMPORT_PATH_NOT_IN_ALLOWLIST));
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.importAllowedPaths.getPropertyName(), "");
        }
    }

    @Test
    public void testExecuteWhenInvalidPathTakesPrecedenceOverAllowlist() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.importAllowedPaths.getPropertyName(), "/tmp/");
        try {
            ImportExportSettings importExportSettings = new ImportExportSettings("$path");
            when(request.getImodel()).thenReturn(importExportSettings);

            try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
                mockedAolUtils.when(AolUtils::isAol).thenReturn(false);
                importSystemService.execute(request, response);
            }
            assertEquals(response.getEntity(),
                    String.format("{\"error\":\"%s\"}", ImportSettings.INVALID_IMPORT_DIR));
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.importAllowedPaths.getPropertyName(), "");
        }
    }

    @Test(testName = "test execute when systemImportEnabled is false")
    public void testExecuteWhenRepositoryImportDisabled() {
        ArtifactoryHome.get()
                .getArtifactoryProperties()
                .setProperty(ConstantValues.systemImportEnabled.getPropertyName(), Boolean.toString(false));

        importSystemService.execute(request, response);
        assertEquals(response.getEntity(), "{\"error\":\"System Import is disabled.\"}");
        assertEquals(response.getResponseCode(), HttpStatus.SC_FORBIDDEN);
    }

    @Test(dataProvider = "actOnMissionControlProvider")
    public void testShouldActOnMissionControl(boolean shouldActOnMissionControl) {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        ImportExportStatusHolder status = new ImportExportStatusHolder();
        ImportExportSettings importExportSettings = new ImportExportSettings("test");
        importExportSettings.setActOnMissionControl(shouldActOnMissionControl);
        File importFromFolder = mock(File.class);
        doAnswer(invocationOnMock -> {
            ImportSettingsImpl argument = invocationOnMock.getArgument(0, ImportSettingsImpl.class);
            Assert.assertEquals(argument.shouldActOnMissionControl(), shouldActOnMissionControl);
            return null;
        }).when(context).importFrom(any(ImportSettings.class));
        importSystemService.importSystem(status, importExportSettings, importFromFolder);
        verify(context, times(1)).importFrom(any(ImportSettings.class));
    }

    @AfterClass
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @DataProvider
    public Object[][] actOnMissionControlProvider() {
        return new Object[][] {
                {true},
                {false}
        };
    }
}
