package org.artifactory.unifiedevent.constants;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SupportedUnifiedEvents {
    AFTER_BUILD_INFO_SAVE("AFTER_BUILD_INFO_SAVE", "afterBuildInfoSave", "org.artifactory.afterBuildInfoSave"),
    AFTER_COPY("AFTER_COPY", "afterCopy", "org.artifactory.afterCopy"),
    AFTER_CREATE("AFTER_CREATE", "afterCreate", "org.artifactory.afterCreate"),
    AFTER_DELETE("AFTER_DELETE", "afterDelete", "org.artifactory.afterDelete"),
    AFTER_DOWNLOAD("AFTER_DOWNLOAD", "afterDownload", "org.artifactory.afterDownload"),
    AFTER_DOWNLOAD_ERROR("AFTER_DOWNLOAD_ERROR", "afterDownloadError", "org.artifactory.afterDownloadError"),
    AFTER_MOVE("AFTER_MOVE", "afterMove", "org.artifactory.afterMove"),
    AFTER_PROPERTY_CREATE("AFTER_PROPERTY_CREATE", "afterPropertyCreate", "org.artifactory.afterPropertyCreate"),
    AFTER_PROPERTY_DELETE("AFTER_PROPERTY_DELETE", "afterPropertyDelete", "org.artifactory.afterPropertyDelete"),
    AFTER_REMOTE_DOWNLOAD("AFTER_REMOTE_DOWNLOAD", "afterRemoteDownload", "org.artifactory.afterRemoteDownload"),
    AFTER_REPO_CREATE("AFTER_REPO_CREATE", "afterRepoCreate", "org.artifactory.afterRepoCreate"),
    AFTER_REPO_DELETE("AFTER_REPO_DELETE", "afterRepoDelete", "org.artifactory.afterRepoDelete"),
    AFTER_REPO_UPDATE("AFTER_REPO_UPDATE", "afterRepoUpdate", "org.artifactory.afterRepoUpdate"),
    ALT_ALL_RESPONSES("ALT_ALL_RESPONSES", "altAllResponses", "org.artifactory.altAllResponses"),
    ALT_REMOTE_CONTENT("ALT_REMOTE_CONTENT", "altRemoteContent", "org.artifactory.altRemoteContent"),
    ALT_REMOTE_PATH("ALT_REMOTE_PATH", "altRemotePath", "org.artifactory.altRemotePath"),
    BEFORE_BUILD_INFO_SAVE("BEFORE_BUILD_INFO_SAVE", "beforeBuildInfoSave", "org.artifactory.beforeBuildInfoSave"),
    BEFORE_COPY("BEFORE_COPY", "beforeCopy", "org.artifactory.beforeCopy"),
    BEFORE_CREATE("BEFORE_CREATE", "beforeCreate", "org.artifactory.beforeCreate"),
    BEFORE_DELETE("BEFORE_DELETE", "beforeDelete", "org.artifactory.beforeDelete"),
    BEFORE_DELETE_REPLICATION("BEFORE_DELETE_REPLICATION", "beforeDeleteReplication",
            "org.artifactory.beforeDeleteReplication"),
    BEFORE_DIRECTORY_REPLICATION("BEFORE_DIRECTORY_REPLICATION", "beforeDirectoryReplication",
            "org.artifactory.beforeDirectoryReplication"),
    BEFORE_DOWNLOAD("BEFORE_DOWNLOAD", "beforeDownload", "org.artifactory.beforeDownload"),
    BEFORE_DOWNLOAD_REQUEST("BEFORE_DOWNLOAD_REQUEST", "beforeDownloadRequest",
            "org.artifactory.beforeDownloadRequest"),
    BEFORE_FILE_REPLICATION("BEFORE_FILE_REPLICATION", "beforeFileReplication",
            "org.artifactory.beforeFileReplication"),
    BEFORE_MOVE("BEFORE_MOVE", "beforeMove", "org.artifactory.beforeMove"),
    BEFORE_PROPERTY_CREATE("BEFORE_PROPERTY_CREATE", "beforePropertyCreate", "org.artifactory.beforePropertyCreate"),
    BEFORE_PROPERTY_DELETE("BEFORE_PROPERTY_DELETE", "beforePropertyDelete", "org.artifactory.beforePropertyDelete"),
    BEFORE_PROPERTY_REPLICATION("BEFORE_PROPERTY_REPLICATION", "beforePropertyReplication",
            "org.artifactory.beforePropertyReplication"),
    BEFORE_REMOTE_DOWNLOAD("BEFORE_REMOTE_DOWNLOAD", "beforeRemoteDownload", "org.artifactory.beforeRemoteDownload"),
    BEFORE_REMOTE_INFO("BEFORE_REMOTE_INFO", "beforeRemoteInfo", "org.artifactory.beforeRemoteInfo"),
    BEFORE_REPO_CREATE("BEFORE_REPO_CREATE", "beforeRepoCreate", "org.artifactory.beforeRepoCreate"),
    BEFORE_REPO_DELETE("BEFORE_REPO_DELETE", "beforeRepoDelete", "org.artifactory.beforeRepoDelete"),
    BEFORE_REPO_UPDATE("BEFORE_REPO_UPDATE", "beforeRepoUpdate", "org.artifactory.beforeRepoUpdate"),
    BEFORE_STATISTICS_REPLICATION("BEFORE_STATISTICS_REPLICATION", "beforeStatisticsReplication",
            "org.artifactory.beforeStatisticsReplication"),
    BEFORE_UPLOAD("BEFORE_UPLOAD", "beforeUpload", "org.artifactory.beforeUpload"),
    ;

    private final String name;
    private final String id;
    @JsonValue
    private final String type;

    /**
     * @return {@code true} for the AFTER repo config CUD events (any constant whose name starts with
     * {@code AFTER_REPO_}). Used to gate the repo CUD JFBus publishing leg independently of other unified events.
     * Relies on naming convention so new AFTER_REPO_* constants are automatically included.
     */
    public boolean isAfterRepoConfigEvent() {
        return name().startsWith("AFTER_REPO_");
    }
}
