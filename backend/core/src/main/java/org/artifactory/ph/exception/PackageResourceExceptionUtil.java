package org.artifactory.ph.exception;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageExpiredException;
import com.jfrog.ph.acl.exception.PackageForbiddenException;
import com.jfrog.ph.acl.exception.PackageNotFoundException;
import com.jfrog.ph.acl.exception.PackagePatternAssertionException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.http.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.security.SecurityService;
import org.artifactory.fs.RepoResource;
import org.artifactory.resource.BlockedRepoResource;
import org.artifactory.resource.ExpiredRepoResource;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.spring.InternalContextHelper;

import java.util.HashMap;
import java.util.Map;

import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.artifactory.api.request.ArtifactoryHeaders.CURATION_ORIGIN;
import static org.artifactory.resource.UnfoundRepoResourceReason.Reason.INCLUDE_EXCLUDE_PATTERN;

/**
 * Utility methods for translating repository resources to package exceptions.
 *
 * @author Shahaf Golan
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PackageResourceExceptionUtil {

    public static PackageException createResourceException(RepoResource info) {
        if (info instanceof UnfoundRepoResource resource) {
            return switch (resource.getStatusCode()) {
                case SC_CONFLICT -> conflictException(info.getRepoKey(), info.getPath(), resource.getDetail());
                case SC_FORBIDDEN -> forbiddenOrNoAuthorization(resource.getStatusCode(), info);
                default -> {
                    if (resource.getReason() == INCLUDE_EXCLUDE_PATTERN) {
                        yield packagePatternAssertionException(resource.getDetail());
                    } else {
                        yield artifactNotFoundException(info.getRepoKey(), info.getPath());
                    }
                }
            };
        }

        if (info instanceof BlockedRepoResource blockedResource) {
            return blockedException(blockedResource.getRepoKey(), blockedResource.getPath(),
                    blockedResource.getMessage(), blockedResource.getBlockedType());
        }

        if (info instanceof ExpiredRepoResource) {
            return resourceExpiredException(info.getRepoKey(), info.getPath());
        }

        return artifactNotFoundException(info.getRepoKey(), info.getPath());
    }

    private static PackagePatternAssertionException packagePatternAssertionException(String msg) {
        return new PackagePatternAssertionException(msg, msg);
    }

    private static PackageException blockedException(String repoKey, String path, String blockedMessage,
            BlockedRepoResource.BlockedType blockedType) {
        Map<String, String> headers = new HashMap<>();
        String publicResourceMessage = "Forbidden: artifact is blocked";
        if (blockedType == BlockedRepoResource.BlockedType.CURATION) {
            headers.put(CURATION_ORIGIN, "true");
            if (StringUtils.isNotBlank(blockedMessage)) {
                publicResourceMessage = blockedMessage;
            }
        }
        String message =
                String.format("Failed to find artifact in repo:%s, path:%s, cause: artifact blocked", repoKey, path);
        return new PackageForbiddenException(publicResourceMessage, message, headers);
    }

    private static PackageException artifactNotFoundException(String repoKey, String path) {
        String message = String.format("Failed to find artifact in repo:%s, path:%s, cause: not found", repoKey, path);
        return new PackageNotFoundException(message);
    }

    private static PackageException conflictException(String repoKey, String path, String cause) {
        String message = String.format("Failed to find artifact in repo:%s, path:%s, cause:%s", repoKey, path, cause);
        return new PackageException(SC_CONFLICT, "Conflict", message);
    }

    private static PackageException forbiddenOrNoAuthorization(int statusCode, RepoResource info) {
        if (shouldSendAuthChallenge(statusCode, info)) {
            return noAuthorization(info.getRepoKey(), info.getPath());
        } else {
            return forbiddenAccess(info.getRepoKey(), info.getPath());
        }
    }

    private static PackageException noAuthorization(String repoKey, String path) {
        String message = String.format(
                "User doesn't have the required authorization to access artifact" + " in repo:%s, path:%s", repoKey,
                path);
        return new PackageException(SC_UNAUTHORIZED, "Unauthorized", message);
    }

    private static PackageException forbiddenAccess(String repoKey, String path) {
        String message = String.format(
                "User doesn't have permissions to access artifact in repo:%s, path:%s", repoKey,
                path);
        return new PackageForbiddenException("Forbidden", message);
    }

    private static PackageException resourceExpiredException(String repoKey, String path) {
        String message =
                String.format("Failed to download artifact in repo:%s, path:%s, cause: artifact is expired",
                        repoKey, path);
        return new PackageExpiredException(message);
    }

    //checks if the resource found, not Expired and not blacked
    public static boolean isValidResource(RepoResource resource, boolean throwIfExpired) {
        return (resource.isFound() || (resource.isExpired() && !throwIfExpired)) && !resource.isBlocked();
    }

    public static void assertValidResource(RepoResource resource) throws PackageException {
        if (!isValidResource(resource, false)) {
            throw createResourceException(resource);
        }
    }

    private static boolean shouldSendAuthChallenge(int status, RepoResource resource) {
        SecurityService securityService = InternalContextHelper.get().getSecurityService();
        return status == HttpStatus.SC_FORBIDDEN && securityService.isAnonymous() && !resource.isBlocked();
    }
}

