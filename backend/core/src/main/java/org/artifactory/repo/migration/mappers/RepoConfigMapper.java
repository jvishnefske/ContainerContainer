package org.artifactory.repo.migration.mappers;

import org.artifactory.repo.RepoDetailsType;

import java.util.Map;

/**
 * Map deprecated repo descriptor into map to have an initial version of repo config.
 */
public interface RepoConfigMapper<D> {
    Map<String, Object> map(D descriptor);
    RepoDetailsType getRepoType();
}