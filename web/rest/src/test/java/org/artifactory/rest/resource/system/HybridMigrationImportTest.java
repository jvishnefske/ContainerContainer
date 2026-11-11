package org.artifactory.rest.resource.system;

import org.apache.http.HttpStatus;
import org.artifactory.aol.AolService;
import org.artifactory.api.common.ImportExportStatusHolder;
import org.artifactory.api.config.ImportService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.config.ImportReport;
import org.artifactory.config.ImportServiceImpl;
import org.artifactory.sapi.common.ImportKind;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.slf4j.Logger;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author igoru
 */
public class HybridMigrationImportTest extends ArtifactoryHomeBoundTest {

    private ArtifactoryContext context;
    private AolService aolService;
    private ImportResource importResource;
    private ImportServiceForTest importService;
    private ImportSettingsConfigurationImpl settings;

    @BeforeMethod
    public void setup() {
        context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(true);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        importService = new ImportServiceForTest();
        importResource = new ImportResource();
        importResource.importService = importService;
        importResource.httpResponse = mock(HttpServletResponse.class);
        when(context.beanForType(ImportService.class)).thenReturn(importService);
        settings = new ImportSettingsConfigurationImpl();
        settings.importPath = System.getProperty("java.io.tmpdir");
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void hybridMigrationDisabledForNotAol() {
        when(aolService.isAol()).thenReturn(false);
        settings.hybridMigration = true;

        Response response = importResource.activateImport(settings);

        assertEquals(response.getStatus(), HttpStatus.SC_FORBIDDEN);
    }

    @Test
    public void whenNoHybridMigrationImportThenAsyncImportNotCalled() {
        importResource.activateImport(settings);

        assertFalse(importService.asyncImportCalled);
        assertEquals(importService.settings.getImportKind(), ImportKind.STANDARD);
    }

    @Test
    public void whenHybridMigrationImportThenAsyncImportCalled() {
        settings.hybridMigration = true;

        importResource.activateImport(settings);

        assertTrue(importService.asyncImportCalled);
        assertEquals(importService.settings.getImportKind(), ImportKind.HYBRID_MIGRATION);
    }

    @Test
    public void whenHybridMigrationImportThenReportSavedToMarkerFile() {
        settings.hybridMigration = true;
        settings.markerFile = "markerFile";

        importResource.activateImport(settings);

        assertEquals(importService.markerFile, "markerFile");
        assertEquals(importService.importReport.getStatus(), "success");
    }

    @Test
    public void testHybridMigrationImportSettings() {
        settings.hybridMigration = true;
        settings.markerFile = "markerFile";
        settings.includeMetadata = true;

        importResource.activateImport(settings);

        assertFalse(importService.settings.isIncludeMetadata());
        assertTrue(importService.settings.isExcludeContent());
        assertEquals(importService.settings.getImportKind(), ImportKind.HYBRID_MIGRATION);
        assertEquals(importService.settings.getMarkerFile(), "markerFile");
    }

    @Test
    public void whenNoErrorsThenImportReportContainsSuccessStatus() {
        ImportExportStatusHolder statusHolder = new ImportExportStatusHolder();

        ImportReport report = ImportReport.createFrom(statusHolder);

        assertEquals(report.getStatus(), "success");
        assertNull(report.getError());
    }

    @Test
    public void whenErrorsThenImportReportContainsFailureStatusAndMessage() {
        ImportExportStatusHolder statusHolder = new ImportExportStatusHolder();
        Logger logger = mock(Logger.class);
        statusHolder.error("message", logger);
        ImportReport report = ImportReport.createFrom(statusHolder);

        assertEquals(report.getStatus(), "failure");
        assertEquals(report.getError(), "message");
    }

    private static class ImportServiceForTest extends ImportServiceImpl {

        boolean asyncImportCalled;
        ImportSettings settings;
        String markerFile;
        ImportReport importReport;

        @Override
        public void importAsync(ImportSettings importSettings) {
            asyncImportCalled = true;
            super.importAsync(importSettings);
        }

        @Override
        public void importSync(ImportSettings importSettings) {
            settings = importSettings;
        }

        @Override
        protected void saveImportReportToMarkerFile(String markerFile, ImportReport importReport) throws IOException {
            this.markerFile = markerFile;
            this.importReport = importReport;
        }
    }
}
