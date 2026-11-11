package org.artifactory.model;

// TODO: 03/02/2022 do we need specific type?

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoDetailsType;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import java.util.HashMap;
import java.util.Objects;

/**
 * Release bundle repository configurations
 *
 * @author shayb
 */
@GenerateDiffFunction
public class ReleaseBundleRepoConfig extends LocalRepoConfig {

    @JsonCreator
    private ReleaseBundleRepoConfig(@JsonProperty("key") String repoKey,
            @JsonProperty("packageType") RepoType packageType) {
        super(repoKey, packageType);
    }

    /**
     * Returns a {@link ReleaseBundleBaseConfig} that always reports the simple-default layout ref,
     * regardless of what is stored on the underlying {@link BaseConfig}.
     * <p>
     * This is intentional: the repo layout of a release-bundle repository is not user-configurable
     * and therefore must never appear as an auditable change in config diffs. The diff engine
     * receives the declared return type {@code BaseConfig} and calls
     * {@code ReleaseBundleBaseConfig.getRepoLayoutRef()}, which always returns
     * {@link org.artifactory.util.RepoLayoutUtils#SIMPLE_DEFAULT_NAME}.
     * <p>
     * If in the future any field of {@link ReleaseBundleBaseConfig} is made instance-driven, the
     * diff engine will naturally pick it up — no changes to the diff infrastructure are required.
     */
    @Override
    public BaseConfig getBaseConfig() {
        return new ReleaseBundleBaseConfig(super.getBaseConfig());
    }

    /**
     * Factory method enabling exclusive access to instance creation
     *
     * @param repoKey     the repository key
     * @return Release bundle repo configuration
     * @throws IllegalArgumentException if {@code repoKey} is blank
     */
    public static ReleaseBundleRepoConfig create(String repoKey) {
        return new ReleaseBundleRepoConfig(repoKey, RepoType.ReleaseBundles);
    }

    /**
     * Factory method enabling exclusive access to deep copy a local repo config
     *
     * @param releaseBundleRepoConfig the release bundle repo config to be copied
     * @return a copy of the release bundle repo config
     * @throws NullPointerException if {@code releaseBundleRepoConfig} is null
     */
    static ReleaseBundleRepoConfig copy(ReleaseBundleRepoConfig releaseBundleRepoConfig) {
        Objects.requireNonNull(releaseBundleRepoConfig, "Release bundle repo config cannot be null");
        ReleaseBundleRepoConfig repoCopy =
                new ReleaseBundleRepoConfig(releaseBundleRepoConfig.getKey(), releaseBundleRepoConfig.getPackageType());
        repoCopy.setBaseConfig(new BaseConfig(releaseBundleRepoConfig.getBaseConfig()));
        repoCopy.setRepoTypeConfig(new LocalRepositoryRepoTypeConfig(releaseBundleRepoConfig.getRepoTypeConfig()));
        repoCopy.setPackageTypeConfig(new HashMap<>(releaseBundleRepoConfig.getPackageTypeConfig()));
        repoCopy.setSecurityConfig(new SecurityConfig(releaseBundleRepoConfig.getSecurityConfig()));
        repoCopy.setModification(releaseBundleRepoConfig.getModification());
        repoCopy.setCreation(releaseBundleRepoConfig.getCreation());
        return repoCopy;
    }

    @Override
    public RepoDetailsType getRepoType() {
        return RepoDetailsType.RELEASE_BUNDLE;
    }
}