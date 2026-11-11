package org.artifactory.util.evidence;

import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsItem;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.ServiceId;

import java.util.List;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.artifactory.common.home.ArtifactoryHome.getBooleanFromSysConfig;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.COLD_INSTANCE_ENABLED;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_AQL_FILTER_IN_DATABASE;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_ENABLED;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_NAVIGATION_ENABLED;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_OCI_ENABLED;
import static org.jfrog.access.common.PermissionScopeResourceType.INTERNAL;
import static org.jfrog.access.common.v2.Action.WRITE;

@Slf4j
@UtilityClass
public class EvidenceConfigurationUtil {

    public static final Pattern EVIDENCE_PATH_PATTERN =
            Pattern.compile("^(?:[^/]+/)?\\.evidence(?:/[^/]+)*/?$"); // NOSONAR

    private static final Pattern PROMOTION_EVIDENCE_FILE_NAME_PATTERN =
            Pattern.compile(".+/promotion-(\\d+)\\.json$");

    public static final String EVIDENCE_DIR = ".evidence";

    public static final String EVIDENCE_PERMISSION_TARGET = "evidence";
    public static final String EVIDENCE_PERMISSION_SUBRESOURCE = "admin";

    public static final PermissionScope EVIDENCE_PERMISSION_SCOPE = PermissionScope.create(
            INTERNAL, EVIDENCE_PERMISSION_TARGET, EVIDENCE_PERMISSION_SUBRESOURCE, WRITE);

    /** Scope carried by every user token; internal-service tokens never have it. */
    private static final String IDENTITY_TOKEN_SCOPE = "applied-permissions/user";

    /**
     * Whether the token belongs to the internal service of {@code expectedServiceType} (e.g. {@code jfds}).
     * <p>
     * A user token is rejected even when its username is {@link ServiceId}-shaped (e.g. {@code jfds@x}),
     * because it carries the {@code applied-permissions/user} scope that a real service token never has.
     */
    public static boolean isInternalServiceToken(@Nullable String subject, @Nullable List<String> scope,
            @NonNull String expectedServiceType) {
        if (subject == null || !ServiceId.isServiceId(subject)) {
            return false;
        }
        if (scope != null && scope.stream().anyMatch(IDENTITY_TOKEN_SCOPE::equalsIgnoreCase)) {
            return false;
        }
        return expectedServiceType.equals(ServiceId.fromFormattedName(subject).getServiceType());
    }

    /**
     * Is the feature toggle for Artifactory evidence enabled?
     */
    public static boolean isEvidenceEnabled() {
        return getBooleanFromSysConfig(EVIDENCE_ENABLED, true);
    }

    public static boolean isEvidenceOCIEnabled() {
        return getBooleanFromSysConfig(EVIDENCE_OCI_ENABLED, true);
    }

    /**
     * Determines if the ".evidence" directories should be filtered from AQL results in DataBase.
     * If the flag is set to false, the filtering will be done in memory.
     */
    public static boolean isEvidenceAQLFilterInDatabase() {
        return getBooleanFromSysConfig(EVIDENCE_AQL_FILTER_IN_DATABASE, true);
    }

    /**
     * Determines whether evidence is shown in Artifactory tree browser.
     * <p>
     * By default, evidence is hidden in the navigation. If this flag is set to true, evidence will be shown.
     * <p>
     * Note: This setting is intended for local development use only.
     */
    public static boolean isEvidenceNavigationDisabled() {
        return !getBooleanFromSysConfig(EVIDENCE_NAVIGATION_ENABLED, false);
    }

    public static boolean isEvidencePath(@Nullable RepoPath repoPath) {
        return nonNull(repoPath) && isEvidencePath(repoPath.getPath());
    }

    public static boolean isEvidencePath(@Nullable VfsItem<? extends ItemInfo> fsItem) {
        return nonNull(fsItem) && isEvidencePath(fsItem.getPath());
    }

    public static boolean isTrashedEvidencePath(@Nullable String path) {
        return nonNull(path) && EVIDENCE_PATH_PATTERN.matcher(path).matches();
    }

    public static boolean isTrashedEvidencePath(@Nullable RepoPath repoPath) {
        if (isNull(repoPath) || !"auto-trashcan".equals(repoPath.getRepoKey())) {
            return false;
        }
        String path = repoPath.getPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.contains("/")) {
            return false;
        }
        String pathWithoutOrigRepoKey = path.substring(path.indexOf("/") + 1);
        return pathWithoutOrigRepoKey.startsWith(EVIDENCE_DIR);
    }

    public static boolean isEvidencePath(@Nullable String path) {
        return nonNull(path) && (path.startsWith(EVIDENCE_DIR + "/") || EVIDENCE_DIR.equals(path));
    }

    public static boolean isEvidenceTopRepoPath(@NonNull VfsItem<? extends ItemInfo> item) {
        return isEvidenceTopRepoPath(item.getRepoPath());
    }

    public static boolean isEvidenceTopRepoPath(@NonNull RepoPath repoPath) {
        return repoPath.getPath().equals(EVIDENCE_DIR) || repoPath.getPath().equals(EVIDENCE_DIR + "/");
    }

    public static boolean isPromotionEvidenceRepoPath(@NonNull RepoPath repoPath) {
        return isEvidencePath(repoPath) && PROMOTION_EVIDENCE_FILE_NAME_PATTERN.matcher(repoPath.getPath()).matches();
    }

    public static boolean isArtifactoryColdInstance() {
        return getBooleanFromSysConfig(COLD_INSTANCE_ENABLED, false);
    }
}