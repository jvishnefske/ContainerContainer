package org.artifactory.storage.db.policy.cleanup;

import lombok.Builder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.artifactory.policy.NodeMetadata;
import org.artifactory.repo.RepoDetailsType;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.jfrog.common.FileUtils.bytesToDisplaySize;


/**
 * @author Hussein Alshqerat
 */
@Builder(toBuilder = true)
public record CleanedPackage(
        String packageName,
        String packageVersion,
        String repo,
        long binLength,
        int status,
        String repoType,
        String message,
        String nodePath,
        String nodeName,
        NodeMetadata nodeMetadata
) {
    public static final long MB = 1024L * 1024;

    public String toPackageKey() {
        return packageName + "|" + packageVersion + "|" + repo + "|" + (message != null ? message : "");
    }

    public static String toPackageKey(CleanedNode node) {
        return node.packageName() + "|" + node.packageVersion() + "|" + node.repo() + "|" +
                (node.message() != null ? node.message() : "");
    }

    private enum ReleaseBundleSkippedMessages {
        DISTRIBUTION_UNAVAILABLE("Distribution service is unavailable."),
        DISTRIBUTION_UNKNOWN_ERROR("Unable to validate distribution status due to unknown reason. " +
                "For more details see artifactory-service.log"),
        VERSION_ALREADY_DISTRIBUTED("Release bundle version is already distributed to one or more edge nodes.");

        private final String message;

        ReleaseBundleSkippedMessages(String message) {
            this.message = message;
        }

        public static String getMessage(String key) {
            try {
                return ReleaseBundleSkippedMessages.valueOf(key).message;
            } catch (IllegalArgumentException e) {
                return DEFAULT_RELEASE_BUNDLE_SKIPPED_MESSAGE;
            }
        }
    }

    private enum BuildSkippedMessages {
        ASSOCIATED_TO_RELEASE_BUNDLE(
                "Deletion of build number was skipped due to an associated release bundle. Set a release bundle " +
                        "retention policy first.");

        private final String message;

        BuildSkippedMessages(String message) {
            this.message = message;
        }

        public static String getMessage(String key) {
            try {
                return BuildSkippedMessages.valueOf(key).message;
            } catch (IllegalArgumentException e) {
                return DEFAULT_BUILD_SKIPPED_MESSAGE;
            }
        }
    }

    private enum BuildPartiallyDeletedMessages {
        FAILURE_ARTIFACTS(
                "Build artifacts were partially deleted. " +
                        "%s of %s artifacts deleted successfully. " +
                        "Please check the remaining artifacts."),
        FAILURE_BUILD_INFO("Unable to delete the build number whereas all the artifacts are deleted. " +
                "For more details see artifactory-service.log");

        private final String message;

        BuildPartiallyDeletedMessages(String message) {
            this.message = message;
        }

        public static String getMessage(String key, String total, String deleted) {
            try {
                return String.format(BuildPartiallyDeletedMessages.valueOf(key).message, deleted, total);
            } catch (IllegalArgumentException e) {
                return DEFAULT_BUILD_PARTIALLY_DELETED_MESSAGE;
            }
        }
    }

    private static final String DEFAULT_SKIPPED_MESSAGE =
            "Unable to delete the package. For more details see artifactory-service.log";

    private static final String DEFAULT_RELEASE_BUNDLE_SKIPPED_MESSAGE =
            "Unable to delete the release bundle version. For more details see artifactory-service.log";

    private static final String DEFAULT_BUILD_SKIPPED_MESSAGE =
            "Unable to delete the build number. For more details see artifactory-service.log";

    private static final String DEFAULT_BUILD_PARTIALLY_DELETED_MESSAGE = "Build was partially deleted";

    private enum PackageSkippedMessages {
        MEMBER_UNAVAILABLE("One or more federation members are unavailable."),
        MEMBER_DISABLED("One or more federation members are disabled."),
        API_ERROR("Unable to validate item across all federation members."),
        API_NOT_SUPPORTED("Item was not found in one or more federation members, " +
                "can also be due to an unsupported version or invalid refPath."),
        FEDERATION_UNKNOWN_ERROR("Unable to validate item across all federation member due to unknown reason. " +
                "For more details see artifactory-service.log"),
        RESTORE_PACKAGE_ALREADY_EXISTS(
                "Package restoration was skipped as the package is already available in the active instance."),
        PACKAGE_MODIFIED("Package at source modified since last archive"),
        RESTORED_PACKAGE("Package was restored at %s");

        private static final DateTimeFormatter RESTORE_TIME_FMT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");

        private final String message;

        PackageSkippedMessages(String message) {
            this.message = message;
        }

        public static String getMessage(String messageKey) {
            if (messageKey == null) {
                return DEFAULT_SKIPPED_MESSAGE;
            }
            String[] parts = messageKey.split("\\|", 2);
            try {
                PackageSkippedMessages entry = PackageSkippedMessages.valueOf(parts[0]);
                if (parts.length > 1) {
                    return String.format(entry.message, formatRestoreTime(parts[1]));
                }
                return entry.message;
            } catch (IllegalArgumentException e) {
                return DEFAULT_SKIPPED_MESSAGE;
            }
        }

        private static String formatRestoreTime(String epochMillis) {
            try {
                return Instant.ofEpochMilli(Long.parseLong(epochMillis))
                        .atZone(ZoneOffset.UTC)
                        .format(RESTORE_TIME_FMT);
            } catch (NumberFormatException e) {
                return epochMillis;
            }
        }
    }

    public CleanedPackage(String packageName, String packageVersion, String repo, long binLength, int status) {
        this(packageName, packageVersion, repo, binLength, status, RepoDetailsType.LOCAL.toString(), null, null, null,
                null);
    }

    public CleanedPackage(String packageName, String packageVersion, String repo, long binLength, int status,
            String message) {
        this(packageName, packageVersion, repo, binLength, status, RepoDetailsType.LOCAL.toString(), message, null,
                null, null);
    }

    public String getPackagePath() {
        StringBuilder path = new StringBuilder();
        if (repo != null) {
            path.append(repo);
        }
        if (StringUtils.isNotBlank(nodePath) && !".".equals(nodePath)) {
            path.append("/").append(nodePath);
        }
        if (nodeName != null) {
            path.append("/").append(nodeName);
        }
        return path.toString();
    }

    public Long getCreatedDate() {
        return nodeMetadata != null ? nodeMetadata.created() : null;
    }

    public Long getModifiedDate() {
        return nodeMetadata != null ? nodeMetadata.modified() : null;
    }

    public Long getLastDownloadedDate() {
        return nodeMetadata != null ? nodeMetadata.lastDownloaded() : null;
    }

    public String[] getData() {
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        return new String[] {
                packageName(),
                packageVersion(),
                String.valueOf(decimalFormat.format((double) binLength / MB)),
                repo(),
                repoType(),
                DeleteArtifactStatus.getByCode(this.status()),
                updateMessage(this.status())
        };
    }

    public String[] getDataWithMetadata() {
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        return new String[] {
                packageName(),
                packageVersion(),
                String.valueOf(decimalFormat.format((double) binLength / MB)),
                repo(),
                repoType(),
                DeleteArtifactStatus.getByCode(this.status()),
                updateMessage(this.status()),
                getPackagePath(),
                formatDate(getCreatedDate()),
                formatDate(getModifiedDate()),
                formatDate(getLastDownloadedDate())
        };
    }

    public String[] getRestoreDataWithMetadata() {
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        return new String[] {
                packageName(),
                packageVersion(),
                String.valueOf(decimalFormat.format((double) binLength / MB)),
                repo(),
                repoType(),
                DeleteArtifactStatus.getByCode(this.status()),
                updateMessage(this.status()),
                getPackagePath(),
                formatDate(getCreatedDate()),
                formatDate(getModifiedDate())
        };
    }

    private String formatDate(Long timestamp) {
        if (timestamp == null || timestamp == 0) {
            return "";
        }
        return DateFormatUtils.format(timestamp, "yyyy-MM-dd HH:mm:ss");
    }

    String updateMessage(int status) {
        switch (status) {
            case 0 -> {
                return "Package was successfully deleted";
            }
            case 1 -> {
                return updateSkippedMessage();
            }
            case 2 -> {
                return "Package delete failed. For more details see artifactory-service.log";
            }
            case 3 -> {
                return "Package was not found";
            }
            case 4 -> {
                return "Package was successfully archived";
            }
            case 5 -> {
                return "Dry run successfully processed the package";
            }
            case 6 -> {
                return "Dry run failed to process the package";
            }
            case 7 -> {
                return "Package restore has not been initialized";
            }
            case 8 -> {
                return "Package is pending warm";
            }
            case 9 -> {
                return "Package was warmed";
            }
            case 10 -> {
                return "Package was successfully restored";
            }
            default -> {
                return "";
            }
        }
    }

    String updateSkippedMessage() {
        return PackageSkippedMessages.getMessage(message);
    }

    public String[] getReleaseBundleData() {
        String created = "";
        String createdBy = "";
        String promotedTo = "";
        if (message != null) {
            String[] messageValues = message.split("\\|");
            if (messageValues.length == 4) {
                created = messageValues[1];
                createdBy = messageValues[2];
                promotedTo = messageValues[3];
            }
        }
        return new String[] {
                packageName(),
                packageVersion(),
                dateToString(created),
                createdBy,
                promotedTo,
                DeleteArtifactStatus.getByCode(this.status()),
                updateReleaseBundleMessage(this.status())
        };
    }

    public String[] getBuildData() {
        String created = "";
        String createdBy = "";
        String totalArtifactsCount = "";
        String associatedRBv2List = "";
        if (message != null) {
            String[] messageValues = message.split("\\|");
            if (messageValues.length == 6) {
                created = messageValues[1];
                createdBy = messageValues[2];
                totalArtifactsCount = messageValues[3];
                associatedRBv2List = messageValues[5];
            }
        }
        return new String[] {
                packageName(),
                packageVersion(),
                dateToString(created),
                createdBy,
                totalArtifactsCount,
                bytesToDisplaySize(binLength()),
                associatedRBv2List,
                DeleteArtifactStatus.getByCode(this.status()),
                updateBuildMessage(this.status())
        };
    }

    String updateReleaseBundleMessage(int status) {
        switch (status) {
            case 0 -> {
                return "Release bundle version was successfully deleted";
            }
            case 1 -> {
                return updateReleaseBundleSkippedMessage();
            }
            case 2 -> {
                return "Release bundle version delete failed. For more details see artifactory-service.log";
            }
            case 5 -> {
                return "Dry run successfully processed the release bundle version";
            }
            case 6 -> {
                return "Dry run failed to process the release bundle version";
            }
            default -> {
                return "";
            }
        }
    }

    String updateBuildMessage(int status) {
        switch (status) {
            case 0 -> {
                return "Build number was successfully deleted";
            }
            case 1 -> {
                return updateBuildSkippedMessage();
            }
            case 2 -> {
                return "Build number delete failed. For more details see artifactory-service.log";
            }
            case 5 -> {
                return "Dry run successfully processed the build number";
            }
            case 6 -> {
                return "Dry run failed to process the build number";
            }
            case 11 -> {
                return updateBuildPartiallyDeletedMessage();
            }
            default -> {
                return "";
            }
        }
    }

    String updateReleaseBundleSkippedMessage() {
        if (message == null) {
            return DEFAULT_RELEASE_BUNDLE_SKIPPED_MESSAGE;
        }
        String[] messageValues = message.split("\\|");
        if (messageValues.length == 4) {
            return ReleaseBundleSkippedMessages.getMessage(messageValues[0]);
        }
        return DEFAULT_RELEASE_BUNDLE_SKIPPED_MESSAGE;
    }

    String updateBuildSkippedMessage() {
        if (StringUtils.isBlank(message)) {
            return DEFAULT_BUILD_SKIPPED_MESSAGE;
        }
        String[] messageValues = message.split("\\|");
        if (messageValues.length == 6) {
            return BuildSkippedMessages.getMessage(messageValues[0]);
        }
        return DEFAULT_BUILD_SKIPPED_MESSAGE;
    }

    String updateBuildPartiallyDeletedMessage() {
        if (StringUtils.isBlank(message)) {
            return DEFAULT_BUILD_PARTIALLY_DELETED_MESSAGE;
        }
        String[] messageValues = message.split("\\|");
        if (messageValues.length == 6) {
            return BuildPartiallyDeletedMessages.getMessage(messageValues[0], messageValues[4], messageValues[3]);
        }
        return DEFAULT_BUILD_PARTIALLY_DELETED_MESSAGE;
    }

    private String dateToString(String date) {
        try {
            return DateFormatUtils.format(Long.parseLong(date), "yyyy-MM-dd HH:mm:ss Z");
        } catch (NumberFormatException e) {
            return "";
        }
    }
}