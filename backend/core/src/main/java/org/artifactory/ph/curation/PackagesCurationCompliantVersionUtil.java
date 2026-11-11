package org.artifactory.ph.curation;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.model.curation.CurationResponse;
import com.jfrog.ph.acl.service.PackageRepoService;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.util.RepoPathUtils;



/**
 * @author Ron Malka
 */

@UtilityClass
@Slf4j
public class PackagesCurationCompliantVersionUtil {

    private static final String CURATED_LOCK = "curated-lock";

    public static final String FORBIDDEN_MARKER_SUFFIX = ".forbiddenMarker";
    public static final String FORBIDDEN_MESSAGE_PROPERTY = "message";
    public static final String PASS_THROUGH_LOCK_SUFFIX = ".passThrough";

    public enum CurationLockKind {
        BUILD,
        PASS_THROUGH
    }

    /**
     * Generates a unique lock key for package curation using repo key and curated path.
     */
    public static String getCuratedLockKey(String repoKey, String curatedPath, CurationLockKind lockKind) {
        String lockPath = switch (lockKind) {
            case BUILD -> curatedPath;
            case PASS_THROUGH -> curatedPath + PASS_THROUGH_LOCK_SUFFIX;
        };
        return String.join("/", CURATED_LOCK, repoKey, lockPath);
    }

    /**
     * Removes the "-cache" suffix from the repo key and validates that the remote repository exists.
     * This is needed for cases where the remote repository contains "cache" in its name.
     */
    public static String getRepoKeyWithoutCache(String repoKey, InternalRepositoryService repositoryService) {
        String remoteRepoKey = RepoPathUtils.removeDashCache(repoKey);
        RemoteRepo<?, ?> remoteRepo = repositoryService.remoteRepositoryByKey(remoteRepoKey);
        if (remoteRepo == null) {
            log.debug("Remote repository {} not found without cache, return the original repo key", repoKey);
            return repoKey;
        }
        return remoteRepoKey;
    }

    /**
     * Extracts the base URL of a remote repository for the given repo key.
     */
    public static String extractRemoteUrl(String repoKey, PackageRepoService packageRepoService) {
        try {
            String remoteUrl = packageRepoService.getRemoteRepoBaseUrl(repoKey);
            if (StringUtils.isNotBlank(remoteUrl) && !remoteUrl.endsWith("/")) {
                remoteUrl = remoteUrl + "/";
            }
            return remoteUrl;
        } catch (PackageException e) {
            log.warn("Failed to extract remote URL for repo: {} with an error: {}", repoKey, e.getMessage());
            log.debug("", e);
        }
        return null;
    }

    /**
     * Verifies that the curation response is valid and contains blocked packages.
     */
    public static boolean verifyCurationResponse(CurationResponse curationResponse) {
        return curationResponse != null && !curationResponse.blockedPackages().isEmpty();
    }

    public static String getForbiddenMarkerPath(String curatePath) {
        return curatePath + FORBIDDEN_MARKER_SUFFIX;
    }

    public static String getDefaultForbiddenMessage(String packageName, String path) {
        if (StringUtils.isBlank(packageName)) {
            return "This Package metadata with path %s is blocked by curation".formatted(path);
        }
        return "This Package metadata %s with path %s is blocked by curation".formatted(packageName, path);
    }

}
