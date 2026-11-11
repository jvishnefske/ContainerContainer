package org.artifactory.importexport;

public class ImportIsDisabledException extends IllegalStateException {
    public static final String REPOSITORIES_IMPORT_IS_DISABLED_MESSAGE = "Repositories Import is disabled.";
    public static final String SYSTEM_IMPORT_IS_DISABLED_MESSAGE = "System Import is disabled.";
    public static final String EXCLUDE_CONTENT_MESSAGE =
            "Cannot import content due to an expired license. Please ensure that the 'Exclude Content' box is checked" +
                    " and attempt the operation again.";

    public ImportIsDisabledException(String message) {
        super(message);
    }

    public static ImportIsDisabledException buildRepositoriesException() {
        return new ImportIsDisabledException(REPOSITORIES_IMPORT_IS_DISABLED_MESSAGE);
    }

    public static ImportIsDisabledException buildSystemException() {
        return new ImportIsDisabledException(SYSTEM_IMPORT_IS_DISABLED_MESSAGE);
    }

    public static ImportIsDisabledException buildExcludeContentException() {
        return new ImportIsDisabledException(EXCLUDE_CONTENT_MESSAGE);
    }
}
