package org.artifactory.repo.virtual;

import org.artifactory.descriptor.repo.VirtualResolverFilter;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author Vova Voloshin
 */
public interface VirtualRepositoryConfigResolver {

    LinkedHashSet<RepoConfig> getRepoConfigs(String key, LinkedHashSet<RepoDetailsType> repoDetailsTypes);

    LinkedHashSet<RepoConfig> getRepoConfigs(String key, LinkedHashSet<RepoDetailsType> repoDetailsTypes,
            VirtualResolverFilter virtualResolverFilter);

    LinkedHashSet<RepoConfig> getRepoConfigs(String key, LinkedHashSet<RepoDetailsType> repoDetailsTypes,
            VirtualResolverFilter virtualResolverFilter, Integer depth);

    LinkedHashSet<RepoConfig> getRepoConfigs(String key, LinkedHashSet<RepoDetailsType> repoDetailsTypes,
            Integer depth);

    List<RealRepoConfig> getOrderedRepos(String key);
}
