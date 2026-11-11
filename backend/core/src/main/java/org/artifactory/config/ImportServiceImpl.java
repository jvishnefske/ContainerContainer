package org.artifactory.config;

import com.google.common.annotations.VisibleForTesting;
import org.artifactory.api.common.ImportExportStatusHolder;
import org.artifactory.api.config.ImportService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.sapi.common.ImportSettings;
import org.jfrog.common.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class ImportServiceImpl implements ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportServiceImpl.class);

    @Override
    public void importAsync(ImportSettings importSettings) {
        ImportExportStatusHolder statusHolder = (ImportExportStatusHolder) importSettings.getStatusHolder();
        try {
            importSync(importSettings);
        } catch (Exception e) {
            statusHolder.error(e.getMessage(), log);
            log.error("Error during async import {}", e.getMessage(), e);
        }
        try {
            ImportReport importReport = ImportReport.createFrom(statusHolder);
            saveImportReportToMarkerFile(importSettings.getMarkerFile(), importReport);
        } catch (Exception e) {
            log.error("Error creating marker file {}, import status {}", e.getMessage(), statusHolder, e);
        }
    }

    @Override
    public void importSync(ImportSettings importSettings) {
        ContextHelper.get().importFrom(importSettings);
    }

    @VisibleForTesting
    protected void saveImportReportToMarkerFile(String markerFile, ImportReport importReport) throws IOException {
        java.nio.file.Path pathToFile = Paths.get(markerFile);
        Files.createDirectories(pathToFile.getParent());
        Files.createFile(pathToFile);
        ByteArrayInputStream stream = new ByteArrayInputStream(JsonUtils.getInstance().valueToByteArray(importReport));
        Files.copy(stream, pathToFile, StandardCopyOption.REPLACE_EXISTING);
        log.info("Import report saved to marker file {}", markerFile);
    }

}
