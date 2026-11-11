package org.artifactory.storage.fs.service;

import org.artifactory.repo.RepoStatDto;
import org.artifactory.spring.RepoConfigChangeListener;

import java.util.List;
import java.util.Set;

public interface RepoStatsService extends RepoConfigChangeListener {

    /**
     * Updates the repository statistics for the given repository key in the cache.
     * For virtual repositories, it also updates the original virtual repository statistics.
     */
    void update(String repoKey, RepoStatDto.RepoStatUpdateType type, String originalRepoKey);

    List<RepoStatDto> getByKeys(Set<String> repoKeys);

    /**
     * Flushes the cached repository statistics to the database.
     * This method should be called periodically from {@link RepoStatsFlushJob}
     */
    void flush();
}
