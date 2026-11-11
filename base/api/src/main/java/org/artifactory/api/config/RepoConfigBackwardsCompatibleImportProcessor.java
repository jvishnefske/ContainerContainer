package org.artifactory.api.config;

import org.artifactory.model.RepoConfig;

import java.util.List;

/**
 * Interface for processing backwards compatible import of repository configurations.
 * Should be used only by {@link org.artifactory.repo.migration.RepoDescriptorsBackwardCompatibleImportProcessor}
 *
 * @author Mor Merhav
 */
public interface RepoConfigBackwardsCompatibleImportProcessor {

    void importRepoConfigs(List<? extends RepoConfig> repoConfigsToImport, boolean reload, boolean propagateChanges);

    void reloadRepoConfigs();

    int countAll();
}