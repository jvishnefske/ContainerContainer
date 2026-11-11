package org.artifactory.util;

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

@Slf4j
@UtilityClass
public class SmartRepoUrlUtils {

    private static final String SMART_URL_TEMPLATE = "{baseUrl}artifactory/api/{packagePrefix}/{repoKey}";

    /**
     * Build smart remote repo url if applicable.
     * @param remoteRepoUrl original remote repo url
     * @param prefixForSmartUrl prefix for smart url according to package type
     * @return smart remote repo url if applicable (Structure: {baseUrl}artifactory/api/{packagePrefix}/{repoKey})
     * Otherwise, original remote repo url. Returns null if the original url is invalid.
     */
    @Nullable
    public static String buildSmartRepoUrl(String remoteRepoUrl, @Nullable String prefixForSmartUrl) {
        try {
            String remoteRepoUrlNoTrailingSlash = removeTrailingSlash(remoteRepoUrl);
            URI uri = new URI(remoteRepoUrlNoTrailingSlash);
            if (isSmartRepoUrlRequired(remoteRepoUrlNoTrailingSlash, prefixForSmartUrl)) {
                return buildSmartRepoUrl(uri, prefixForSmartUrl);
            }
        } catch (URISyntaxException e) {
            log.warn("Could not get smart repo URL for remote URL '{}': {}", remoteRepoUrl, e.getMessage());
            return null;
        }
        return remoteRepoUrl;
    }

    /**
     * Check if smart repo url is required. Indicators are:
     * - Standard Artifactory remote repo url
     * - Prefix for smart url is not null
     * - Remote repo url is not already in the smart repo url format
     * @param remoteRepoUrl remote repo url
     * @param prefixForSmartUrl prefix for smart url according to package type
     * @return true if smart repo url is required, false otherwise
     */
    public static boolean isSmartRepoUrlRequired(String remoteRepoUrl, String prefixForSmartUrl) {
        if (remoteRepoUrl == null || prefixForSmartUrl == null) {
            return false;
        }
        try {
            URI uri = new URI(remoteRepoUrl);
            return isArtifactoryRequest(uri) && matchesStandardUrlFormat(remoteRepoUrl) &&
                    isNotAlreadySmartRepoUrl(remoteRepoUrl, prefixForSmartUrl);
        } catch (URISyntaxException e) {
            log.warn("Invalid URI for remote repo URL '{}': {}. Returning 'false' for smart repo URL requirement check",
                    remoteRepoUrl, e.getMessage());
            return false;
        }
    }

    private static boolean isNotAlreadySmartRepoUrl(String remoteRepoUrl, String prefixForSmartUrl) {
        return !matchesSmartUrlFormat(remoteRepoUrl, prefixForSmartUrl);
    }

    /**
     * Checks if a URL matches the standard Artifactory format:
     * &lt;schema&gt;://&lt;host&gt;[&lt;:port&gt;]/artifactory/&lt;repoKey&gt;
     * The URL must end exactly after the repoKey (no query params, fragment, or additional path).
     *
     * @param urlString the URL string to check
     * @return true if the URL matches the standard format, false otherwise
     */
    public static boolean matchesStandardUrlFormat(String urlString) {
        if (urlString == null) {
            return false;
        }
        Pattern standardPattern = getStandardUrlPattern();
        return standardPattern.matcher(urlString).matches();
    }

    /**
     * Checks if a URL matches the smart Artifactory format:
     * &lt;schema&gt;://&lt;host&gt;[&lt;:port&gt;]/artifactory/api/&lt;packagePrefix&gt;/&lt;repoKey&gt;
     * The URL must end exactly after the repoKey (no query params, fragment, or additional path).
     *
     * @param urlString the URL string to check
     * @param packagePrefix the package type prefix to match
     * @return true if the URL matches the smart format, false otherwise
     */
    public static boolean matchesSmartUrlFormat(String urlString, String packagePrefix) {
        if (urlString == null || packagePrefix == null) {
            return false;
        }
        Pattern smartPattern = buildSmartUrlPattern(packagePrefix);
        return smartPattern.matcher(urlString).matches();
    }

    /**
     * Builds a regex pattern that matches the standard Artifactory URL format:
     * &lt;schema&gt;://&lt;host&gt;[&lt;:port&gt;]/artifactory/&lt;repoKey&gt;
     * where [&lt;:port&gt;] means the port is optional.
     * The URL must end exactly after the repoKey (no query params, fragment, or additional path).
     *
     * @return a Pattern that matches standard Artifactory URLs
     */
    private static Pattern getStandardUrlPattern() {
        // Pattern breakdown:
        // - ^[^:]+:// matches scheme (http://, https://, etc.)
        // - [^/?#]+ matches host and optional port (stops at /, ?, or #)
        // - /artifactory/ matches the fixed path
        // - [^/?#]+ matches repoKey (at least one character, stops at /, ?, or #)
        // - $ ensures the URL ends exactly after the repoKey (no query params, fragment, or additional path)
        String pattern = "^[^:]+://[^/?#]+/artifactory/[^/?#]+$";
        return Pattern.compile(pattern);
    }

    /**
     * Builds a regex pattern that matches the smart Artifactory URL format:
     * &lt;schema&gt;://&lt;host&gt;[&lt;:port&gt;]/artifactory/api/&lt;packagePrefix&gt;/&lt;repoKey&gt;
     * where [&lt;:port&gt;] means the port is optional.
     * The URL must end exactly after the repoKey (no query params, fragment, or additional path).
     *
     * @param packagePrefix the package type prefix to match (will be escaped for regex safety)
     * @return a Pattern that matches smart Artifactory URLs with the given package prefix
     */
    private static Pattern buildSmartUrlPattern(String packagePrefix) {
        // Escape special regex characters in packagePrefix to prevent regex injection
        String escapedPrefix = Pattern.quote(packagePrefix);
        // Pattern breakdown:
        // - ^[^:]+:// matches scheme (http://, https://, etc.)
        // - [^/?#]+ matches host and optional port (stops at /, ?, or #)
        // - /artifactory/api/ matches the fixed indicator path
        // - {escapedPrefix}/ matches the package prefix followed by /
        // - [^/?#]+ matches repoKey (at least one character, stops at /, ?, or #)
        // - $ ensures the URL ends exactly after the repoKey (no query params, fragment, or additional path)
        String pattern = "^[^:]+://[^/?#]+/artifactory/api/" + escapedPrefix + "/[^/?#]+$";
        return Pattern.compile(pattern);
    }

    private static String buildSmartRepoUrl(URI uri, String packagePrefix) {
        URI uriBase = uri.resolve("/");
        return SMART_URL_TEMPLATE.replace("{baseUrl}", uriBase.toString())
                .replace("{packagePrefix}", packagePrefix)
                .replace("{repoKey}", getRepoKey(uri));
    }

    private static boolean isArtifactoryRequest(URI uri) {
        String baseUri = uri.resolve("/").toString();
        return uri.toString().startsWith(baseUri + "artifactory/");
    }

    private static String getRepoKey(URI uri) {
        String[] elements = uri.getPath().split("/");
        return elements[elements.length - 1];
    }

    private String removeTrailingSlash(String remoteRepoUrl) {
        return Strings.CS.removeEnd(remoteRepoUrl, "/");
    }
}