package org.artifactory.api.repo.validation;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import software.amazon.awssdk.utils.CollectionUtils;

import javax.annotation.Nonnull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@UtilityClass
@Slf4j
public class RepoUrlValidator {

    /**
     * <p>Validates that the given url is not pointing to the repository itself.
     * <p>Skipping validation if baseUrl is missing or the remote url host is different than the baseUrl host.
     * <p>Example:
     * <pre>
     *      For repository: spm-demo-repo-remote, if the following is defined:
     *          repoKey: spm-demo-repo-remote
     *          url: "<a href="https://servername.jfrog.io/artifactory/api/swift/spm-demo-repo-remote">...</a>"
     *
     * </pre>
     *
     * @throws IllegalArgumentException in case of self/circular pointing.
     */
    public static void validateNoSelfPointingUrl(@Nonnull String baseServerHost, String repoKey, String url)
            throws IllegalArgumentException {
        if (repoKey == null || StringUtils.isBlank(url)) {
            return;
        }
        URI remoteUri = getRemoteUri(url);
        if (remoteUri == null || remoteUri.getHost() == null) {
            return;
        }
        if (isDifferentArtifactoryServer(baseServerHost, remoteUri.getHost())) {
            log.debug("Remote url host '{}' is different than base url host '{}', skipping self-pointing validation",
                    remoteUri.getHost(), baseServerHost);
            return;
        }

        List<String> segments = new URIBuilder(remoteUri).getPathSegments();
        if (CollectionUtils.isNullOrEmpty(segments)) {
            return;
        }
        String repoKeyFromUrl = segments.getLast();
        if (repoKey.equals(repoKeyFromUrl)) {
            String errMsg = "Invalid repository url: '%s', repo points to itself for repoKey: '%s'"
                    .formatted(url, repoKey);
            log.warn(errMsg);
            throw new IllegalArgumentException(errMsg);
        }
    }

    private static boolean isDifferentArtifactoryServer(String baseServerHost, String remoteHost) {
        return !baseServerHost.equalsIgnoreCase(remoteHost);
    }

    private static URI getRemoteUri(String remoteUrl) {
        try {
            return new URI(remoteUrl);
        } catch (URISyntaxException e) {
            log.debug("Failed to parse remote url: '{}', skipping self-pointing validation", remoteUrl, e);
            return null;
        }
    }
}
