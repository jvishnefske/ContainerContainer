package org.artifactory.throttling.lh.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * enum LoadHealerActivity is used to define the available activities that can be registered in the Load Healer.
 * The name of the activity is used to configure the activity properties in the YAML configuration file.
 * The activity name should be unique and single word without special characters.
 *
 * @author Hussein Alshqerat
 */
@Getter
@AllArgsConstructor
public enum LoadHealerActivity {

    EXAMPLE("example"),
    TEST("test"),
    XRAY_BLOCK_UNSCANNED("xrayBlockUnscanned"),
    XRAY_DOWNLOAD_BLOCKED("xrayDownloadBlocked"),
    QRL_LOW_PRIORITY_REQUESTS("qrlLowPriorityRequests"),
    CURATION_CACHED_PACKAGE_STATUS("curationCachedPackageStatus"),
    PARALLEL_UPLOAD_PRE_REPO_KEY("parallelUploadPreRepoKey"),
    PARALLEL_DOWNLOAD_UPLOAD_PER_PACKAGE_TYPE("parallelDownloadUploadPerPackageType"),
    LIMIT_ACTIVITY("limitActivity"),
    LIMIT_MODULE("limitModule"),
    LIMIT_END_POINT("limitEndPoint"),
    LIMIT_USER("limitUser");


    private final String activityName;
}