package org.artifactory.addon.retention;

import lombok.experimental.UtilityClass;

/**
 * @author Aviv Anidjar
 */
@UtilityClass
public final class ArtifactRetentionConstants {
    public static final String RETENTION_COLD_INSTANCE_MISSING_LICENSE = "Cold instance license is not set";
    public static final String RETENTION_COLD_INSTANCE_INVALID_VERSION =
            "Cold instance version must be: 7.21.0 or higher but was: %s";
    public static final String PACKAGE_RETENTION_COLD_INSTANCE_INVALID_VERSION =
            "Please upgrade cold instance at least to 7.63 version to archive/restore packages";
    public static final String RETENTION_LICENSES_EQUAL_MESSAGE =
            "Warm and cold instance licenses cannot be the same";
    public static final String RETENTION_FAILED_TO_VALIDATE_LICENSES_MESSAGE =
            "Failed to validate warm and cold instance licenses";
    public static final String RETENTION_UNSUPPORTED_BY_LICENSE_MESSAGE =
            "Retention is available only on Enterprise licensed Artifactory instances.";
    public static final String RETENTION_DISABLED_MESSAGE = "Retention is not enabled";
    public static final String PACKAGE_RETENTION_DISABLED_MESSAGE = "Package retention is disabled";
    public static final String RETENTION_POLICY_DISABLED_MESSAGE = "Retention policy is disabled";
    public static final String COLD_INSTANCE_URL_IS_MISSING =
            "Cold instance is not paired. Please pair cold instance before running";
    public static final String RETENTION_WRONG_INSTANCE_MESSAGE =
            "Request cannot be executed on the %s instance. Please try to execute this request on the %s instance";
    public static final String RETENTION_UNABLE_TO_EXECUTE_RESTORE =
            "Unable to execute restore: Archiving Namespace is not set";
    public static final String RESTORE_IN_TRANSIT_REPO_KEY = "restore-in-transit";
    public static final String COLD_INSTANCE_NOT_AVAILABLE = "Cold instance is not available: ";
    public static final String RETENTION_ARCHIVE_COMMON_PREFIX = "retention_archive_";
    public static final String RETENTION_ARTIFACT_ARCHIVE_PREFIX = RETENTION_ARCHIVE_COMMON_PREFIX + "artifact_";
    public static final String RETENTION_PACKAGE_ARCHIVE_PREFIX = RETENTION_ARCHIVE_COMMON_PREFIX + "package_";
    public static final String RETENTION_RESTORE_COMMON_PREFIX = "retention_restore_";
    public static final String RETENTION_PROP_PREFIX = "artifact.retention.";
    public static final String ARTIFACT_STATUS_PROP_KEY = RETENTION_PROP_PREFIX + "restore.status";
    public static final String ARTIFACT_ARCHIVE_TIME_PROP_KEY = RETENTION_PROP_PREFIX + "archive.time";
    public static final String ARTIFACT_ARCHIVE_EXPIRATION_TIME_PROP_KEY =
            RETENTION_PROP_PREFIX + "archive.expiration.time";
    public static final String TOO_MANY_CONCURRENT_REQUESTS_ERROR_MESSAGE =
            "Skip archive policy %s execution. Too many concurrent archive policies executions. Allowed count %s. " +
                    "Allowed count might be changed in system.properties file";
    public static final String ARCHIVE_CONFLICT_GUARD_NAME = "retention_archive";
    public static final String ARTIFACT_RETENTION_LOCK_CATEGORY = "artifact_retention";
    public static final String RETENTION_POLICY_DESCRIPTION_PREFIX = "Retention Policy - ";
    public static final long ARCHIVE_CONFLICT_LOCK_TIMEOUT_IN_SECONDS = 10;
    public static final String WARM_AND_COLD_NOT_PAIRED = "Cold and warm instances are not paired";
    public static final String MASTER_TOKEN_EXPIRED_PATTERN = "Master token for cold instance is expired. " +
            "Please pair warm and cold instances again. Use '%s' namespace to keep relation between warm and cold " +
            "otherwise you will not be able to restore archived data. Archived data will not be deleted.";
    public static final String RESTORE_OPERATION_INITIALIZED_SUCCESSFULLY =
            "Restore process was initialized successfully.";
    public static final String RESTORE_OPERATION_FAILED =
            "Restoration operation failed, some of the requested files could not be restored";
    public static final String INSTANCES_ALREADY_PAIRED = "Failed to pair warm and cold instances, please unpair first";
    public static final String MASTER_TOKEN_NOT_FOUND =
            "Unable to find jfrog cold storage master token with matching URL: ";
    public static final String REPO_KEY_IS_NOT_PROVIDED = "Repository key is not provided";
    public static final String RETENTION_ARTIFACT_RESTORE_PREFIX = "retention_restore_artifact_";
    public static final String RETENTION_PACKAGE_RESTORE_PREFIX = "retention_restore_package_";
    public static final String PACKAGE_MANIFESTS_REPO_NAME = "archived-packages-metadata";
    public static final String PACKAGE_TYPE_PROP_KEY = "archive.packageType";
    public static final String PACKAGE_NAME_PROP_KEY = "archive.packageName";
    public static final String VERSION_PROP_KEY = "archive.version";
    public static final String ORIG_REPO_PROP_KEY = "archive.repoName";
    public static final String ARCHIVE_CREATED_PROP_KEY = "archive.created";
    public static final String ARCHIVE_TIME_PROP_KEY = "archive.time";
    public static final String ARCHIVE_ARTIFACT_COUNT_PROP_KEY = "archive.artifactCount";
    public static final String RESTORE_TIME_PROP_KEY = "restore.time";

}