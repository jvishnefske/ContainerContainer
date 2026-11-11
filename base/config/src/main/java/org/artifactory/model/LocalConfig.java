package org.artifactory.model;

/**
 * Interface for local repository configurations. This interface represent a local repository configurations in the
 * system, but also other local-type repositories such as Federated repository.
 *
 * @author shayb
 */
public interface LocalConfig extends RealRepoConfig {

    @Override
    LocalRepositoryRepoTypeConfig getRepoTypeConfig();

    @Override
    default boolean isLocal() {
        return true;
    }
}
