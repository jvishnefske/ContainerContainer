package org.artifactory.config;

import lombok.Data;
import org.artifactory.api.common.ImportExportStatusHolder;

/**
 * @author igoru
 */
@Data
public class ImportReport {
    private String status;
    private String error;

    public static ImportReport createFrom(ImportExportStatusHolder statusHolder) {
        ImportReport importReport = new ImportReport();
        if (statusHolder.hasErrors()) {
            importReport.status = "failure";
            importReport.error = statusHolder.getLastError().getMessage();
        } else {
            importReport.status = "success";
        }
        return importReport;
    }
}