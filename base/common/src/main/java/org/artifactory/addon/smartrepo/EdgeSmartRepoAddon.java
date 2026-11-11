package org.artifactory.addon.smartrepo;

import org.artifactory.common.addon.Addon;
import org.artifactory.addon.license.EdgeAddon;
import org.artifactory.api.repo.UpstreamRepoKind;
import org.artifactory.entitlement.Entitlement;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.model.RemoteRepoConfig;

/**
 * This Addon purpose is to make sure that all remote repositories on Edge are smart repositories.
 *
 * @author Inbar Tal
 */
@EdgeAddon
@Entitlement(EntitlementType.EDGE)
public interface EdgeSmartRepoAddon extends Addon {

    /**
     * On Edge instances (denoted by {@link EdgeAddon}), replication is not allowed.
     */
    default void validateReplication() {
    }

    /**
     * Call this method in the configuration state to verify upstream repo config is correct.
     * @param remoteRepoConfig
     */
    default UpstreamRepoKind getUpstreamRepoKind(RemoteRepoConfig remoteRepoConfig) {
        return UpstreamRepoKind.SMART;
    }

    /**
     * Call this method in the configuration state to get the current state of upstream repo.
     */
    default UpstreamRepoKind updateUpstreamRepoKind(String repoKey) {
        return UpstreamRepoKind.SMART;
    }
}
