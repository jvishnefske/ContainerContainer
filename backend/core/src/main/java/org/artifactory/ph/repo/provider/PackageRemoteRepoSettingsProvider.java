package org.artifactory.ph.repo.provider;

import org.artifactory.model.RemoteRepoConfig;

import javax.annotation.Nonnull;

/**
 * Interface for providing pre-defined configurations related to remote repositories.
 */
public interface PackageRemoteRepoSettingsProvider {

    /**
     * Determines if package type is supported.
     *
     * @param config The remote repo config of the remote repository.
     * @return {@code true} if this provider's package type is supported.
     */
    boolean isPackageTypeSupported(@Nonnull RemoteRepoConfig config);

    /**
     * Determines if this provider should be applied to the given remote repository.
     *
     * @param config The remote repo config of the remote repository.
     * @return {@code true} if this provider's settings should be applied.
     */
    boolean isApplicable(@Nonnull RemoteRepoConfig config);

    /**
     * Applies the pre-defined settings to the repository configuration.
     *
     * @param config The remote repository configuration to modify.
     */
    void applySettings(@Nonnull RemoteRepoConfig config);

}
