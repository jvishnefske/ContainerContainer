package org.artifactory.ph;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;

public interface PackageSpecification {

    /**
     * Indicates whether the specified path is a package in the given repository key.
     *
     * @return true if the artifact path represent a package
     */
    boolean isPackageArtifact(@Nonnull String repoKey, @Nonnull String path);

    /**
     * The method is used by Artifactory to identify whether the package type & repo type are supporting reindex
     */
    boolean hasReindex(org.artifactory.common.repo.RepoType repoType, RepoType packageType);

    /**
     * The method is used by Artifactory to identify whether the package type supports path-specific (partial) reindex.
     * Path reindex rebuilds index.json files scoped to the given path and always rebuilds packages.ref.json fully.
     */
    boolean hasPathReindex(RepoType packageType);

    /**
     * The method is used by Artifactory to identify whether the artifact is viewable from Artifactory UI
     */
    boolean isViewable(@Nonnull String repoKey, @Nonnull String path, RepoType repoPkgType);

    /**
     * Indicates whether the specified path is a package in the given repository key.
     *
     * @return true if the artifact path represent a package
     */
    boolean isPackageArtifact(@Nonnull String repoKey, @Nonnull String path, @Nonnull RepoType repoType);

    /**
     * Determine if virtual repo can aggregate the given repo according to package type
     */
    boolean isVirtualCanContainRepoWithType(RepoType virtualRepoType,RepoType aggregatedRepoType);

    /**
     * Determines whether the repository has a migration action based on the package type and repository configuration.
     * The `hasMigrationAction` field may be `null` to exclude it from serialization, ensuring it does not appear in
     * the JSON output when unset.
     */
    @Nullable
    Boolean hasMigrationAction(@Nonnull RepoConfig repoConfig);

    /**
     * Get prefix for smart repository URL according to package type if applicable
     * @param repoType package type
     * @return prefix for smart repository URL if applicable, null otherwise
     */
    @Nullable
    String getPrefixForSmartUrl(RepoType repoType);
}