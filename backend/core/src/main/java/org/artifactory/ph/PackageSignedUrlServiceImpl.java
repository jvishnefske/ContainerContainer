package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.service.PackageSignedUrlService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.signature.SignedUrlAuthenticationToken;
import org.artifactory.signature.SignedUrlService;
import org.jfrog.common.ExceptionLogUtils;
import org.springframework.security.core.Authentication;

/**
 * @author Volodymyr Shulga
 */
@Slf4j
public class PackageSignedUrlServiceImpl implements PackageSignedUrlService {
    private final SignedUrlService signedUrlService;

    public PackageSignedUrlServiceImpl(SignedUrlService signedUrlService) {
        this.signedUrlService = signedUrlService;
    }

    @NonNull
    public String createSignedUrl(@NonNull String serverUrl, @NonNull String path, @Nullable String internalRepoKey,
            @Nullable Long validForSeconds, boolean isUiRequest) throws PackageException {
        try {
            if (StringUtils.isBlank(internalRepoKey)) {
                return signedUrlService.createSignedUrl(serverUrl, path, validForSeconds, isUiRequest);
            } else {
                return signedUrlService.createSignedUrl(serverUrl, path, internalRepoKey, validForSeconds, isUiRequest);
            }
        } catch (Exception e) {
            log.warn("Failed to sign url for params: serverUrl: '{}', path: '{}', internalRepoKey: '{}', " +
                            "validForSeconds: {}, isUiRequest: {}. Message: '{}'", serverUrl, path, internalRepoKey,
                    validForSeconds, isUiRequest, e.getMessage());
            log.debug("Failed to sign url for params: serverUrl: '{}', path: '{}', internalRepoKey: '{}', " +
                            "validForSeconds: {}, isUiRequest: {}.", serverUrl, path, internalRepoKey,
                    validForSeconds, isUiRequest, e);
            throw new PackageException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Failed to sign url", e.getMessage(), e);
        }
    }

    @NonNull
    public String createSignedUrl(@NonNull String serverUrl, @NonNull String path, @Nullable String internalRepoKey,
            @Nullable Long validForSeconds) throws PackageException {
        try {
            return signedUrlService.createSignedUrl(serverUrl, path, internalRepoKey, validForSeconds, false);
        } catch (Exception e) {
            String msg = String.format(
                    "Failed to sign url for params: serverUrl: '%s', path: '%s', internalRepoKey: '%s', " +
                            "validForSeconds: %s", serverUrl, path, internalRepoKey, validForSeconds);
            ExceptionLogUtils.debugOrWarn(log, msg, e);
            throw new PackageException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Failed to sign url", e.getMessage(), e);
        }
    }

    @Nullable
    public String getInternalRepoFromSignedUrlAuthentication() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (authentication instanceof SignedUrlAuthenticationToken signedUrlAuthentication) {
            return signedUrlAuthentication.getInternalRepo();
        }
        return null;
    }
}
