package org.artifactory.hqc.enums;

/**
 * @author Tomer Slutsky
 * @author Andrey Tashlitsky
 */
public class HeavyQueryCacheType {

    public static final String FEDERATED_FULL_SYNC = "hqc_federated_full_sync";
    public static final String REMOTE_FEDERATED_FULL_SYNC = "hqc_remote_federated_full_sync";
    public static final String TOTAL_ARTIFACT_COUNT = "hqc_total_artifact_count";
    public static final String XRAY_INDEXES = "hqc_xray_indexes";
    public static final String TOTAL_ARTIFACT_PER_REPO = "hqc_federated_total_artifact_per_repo";
    public static final String STORAGE_SIZE = "storage_size";
    public static final String STORAGE_INFO = "hqc_storage_info";
    public static final String DOCKER_UPLOADS_CLEANUP = "hqc_docker_uploads_cleanup";
}
