package org.artifactory.api.repo;

import org.artifactory.model.RemoteRepoConfig;

/**
 * Service used to discover capabilities of another artifactory
 *
 * @author michaelp
 */
public interface ResearchService {

    /**
     * Discovers the kind of the remote repository's upstream by contacting it
     * and checking whether it is an Artifactory instance and whether it
     * supports smart remote behavior.
     *
     * @param remoteRepoConfig the remote repository configuration (URL,credentials, proxy, etc.)
     *
     * @return the upstream kind (e.g. {@link UpstreamRepoKind#SMART},
     *         {@link UpstreamRepoKind#NOT_ARTIFACTORY},
     *         {@link UpstreamRepoKind#NOT_AVAILABLE}); never null
     */
    UpstreamRepoKind getUpstreamRepoKind(RemoteRepoConfig remoteRepoConfig);

    /**
     * Returns whether the remote repository is configured to synchronize
     * properties from the upstream. This is true if either the legacy
     * {@code synchronizeProperties} flag is set or the Smart Remote
     * {@code contentSynchronisation} is enabled with properties enabled.
     * Does not consider URL blacklists; callers should also use
     * {@link #isUpstreamUrlBlockedForPropertySync(String)} before
     * sending property sync requests.
     *
     * @param repoConfig the remote repository configuration
     * @return {@code true} if property sync is configured, {@code false}
     *         otherwise
     */
    boolean isRepoConfiguredToSyncProperties(RemoteRepoConfig repoConfig);

    /**
     * Checks whether the given upstream URL is blocked from property
     * synchronization. A URL is blocked if it appears in the built-in
     * default list (known public registries) or in the admin-configured
     * blacklist ({@code syncPropertiesBlacklistUrls}).
     *
     * @param upstreamUrl the remote repository upstream URL
     * @return {@code true} if the URL is blocked, {@code false} otherwise
     */
    boolean isUpstreamUrlBlockedForPropertySync(String upstreamUrl);

    /**
     * Returns whether the upstream URL matches a known public registry
     * from the built-in default list only (e.g. Docker Hub, npmjs,
     * Maven Central). Unlike {@link #isUpstreamUrlBlockedForPropertySync},
     * this does <b>not</b> consider the admin-configured blacklist.
     *
     * @param upstreamUrl the remote repository upstream URL
     * @return {@code true} if the URL is a known public registry
     */
    boolean isKnownPublicRegistryUrl(String upstreamUrl);

}
