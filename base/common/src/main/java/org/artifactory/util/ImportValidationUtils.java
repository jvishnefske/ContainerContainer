package org.artifactory.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.sapi.common.ImportKind;
import org.artifactory.sapi.common.ImportSettings;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ImportValidationUtils {
    private static final String IMPORT_NOT_SUPPORTED_MESSAGE =
            "This import is available only for Artifactory self-hosted instances";

    /**
     * Use this method to determine if a given import should be executed or not, in terms of security aspects
     *
     * @param settings   - ImportSettings to check
     * @param importName - Name of the import - for log usages
     */
    public static boolean shouldImport(ImportSettings settings, String importName) {
        if (settings.getImportKind().equals(ImportKind.HYBRID_MIGRATION) || AolUtils.isAol()) {
            log.info("Skipping " + importName + " import. " + IMPORT_NOT_SUPPORTED_MESSAGE);
            return false;
        }
        return true;
    }
}
