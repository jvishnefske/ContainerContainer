package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.TreeFilter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.RepositoryNode;
import org.artifactory.util.CollectionUtils;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.commons.continues.ContinueResult;
import org.jfrog.commons.continues.FetchFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.artifactory.common.ConstantValues.treeBrowserImprovedShowRepoInTreeEnabled;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE_REPO;
import static org.artifactory.repo.RepoPath.REMOTE_CACHE_SUFFIX;
import static org.artifactory.repo.system.SystemRepos.RELEASE_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.VirtualRemoteRepositoryNode.fromRemoteDescriptor;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.VirtualRemoteRepositoryNode.fromRemoteRepoConfig;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.VirtualRemoteRepositoryNode.fromVirtualDescriptor;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.VirtualRemoteRepositoryNode.fromVirtualRepoConfig;
import static org.artifactory.util.bundles.BundleConfigurationUtil.APP_VERSIONS_REPO;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;
import static org.jfrog.commons.continues.util.PagingUtils.getPagingFromMultipleFunctions;

/**
 * @author Omri Ziv
 */
public abstract class RootFetchStrategy {

    private static final Logger log = LoggerFactory.getLogger(RootFetchStrategy.class);
    TreeFilter treeFilter;
    RepositoryService repositoryService;
    RepositoryConfigService repositoryConfigService;
    String projectKey;
    private String nextContinueState;
    private final boolean defaultContext;
    final SystemRepoFetchService fetchService;
    final DescriptorFilter descriptorFilter;
    protected final Predicate<RepoConfig> virtualRepoVisibility;

    RootFetchStrategy(TreeFilter treeFilter) {
        this.treeFilter = treeFilter;
        ArtifactoryContext artifactoryContext = ContextHelper.get();
        this.repositoryService = artifactoryContext.getRepositoryService();
        this.repositoryConfigService = artifactoryContext.getRepositoryConfigService();
        this.projectKey = Optional.ofNullable(treeFilter.getProjectKey())
                .filter(StringUtils::isNotEmpty)
                .orElse(ProjectsService.DEFAULT_PROJECT);
        defaultContext = ProjectsUtils.isDefaultProject(projectKey);
        this.descriptorFilter = new DescriptorFilter(treeFilter);
        this.fetchService = new SystemRepoFetchService(treeFilter, defaultContext, descriptorFilter);
        this.virtualRepoVisibility = artifactoryContext.beanForType(ProjectsService.class)
                .createVirtualReposVisibilityFilter(projectKey);
    }

    static <T extends RepoDescriptor> int getLastIndexOfKeys(List<String> originalRepoKeys,
            List<T> filteredRepoDescriptor,
            int limit) {
        return CollectionUtils.notNullOrEmpty(filteredRepoDescriptor) && filteredRepoDescriptor.size() == limit
                ? originalRepoKeys.indexOf(filteredRepoDescriptor.get(filteredRepoDescriptor.size() - 1).getKey()) + 1
                : originalRepoKeys.size();
    }

    static <T extends RepoDescriptor> List<T> sortAndGet(List<T> original) {
        return StreamSupportUtils.stream(original)
                .sorted(new RootFetchStrategy.RepoComparator())
                .collect(Collectors.toList());
    }

    static <T extends RepoConfig> List<T> sortAndGetRepoConfigs(List<T> original) {
        return StreamSupportUtils.stream(original)
                .sorted(new RootFetchStrategy.RepoConfigComparator())
                .toList();
    }

    static Integer adjustLimitInCaseShowRepoInTree(TreeFilter treeFilter, List<?> repos, Integer limit) {
        String repoKeyToInclude = treeFilter.getMustInclude();
        if (shouldSkipAdjustLimit(treeFilter, repoKeyToInclude)) {
            return limit;
        }
        int repoKeyIndex = -1;
        for (int i = 0; i < repos.size(); i++) {
            Object o = repos.get(i);
            String key = null;
            if (o instanceof RepoConfig repoConfig) {
                key = repoConfig.getKey();
            } else if (o instanceof RepoDescriptor repoDescriptor) {
                key = repoDescriptor.getKey();
            }
            if (repoKeyToInclude.equals(key)) {
                repoKeyIndex = i;
                break;
            }
        }
        log.debug("Index of repo '{}': {}", repoKeyToInclude, repoKeyIndex);
        if (repoKeyIndex == -1) {
            return repos.size();
        }
        return repoKeyIndex + limit - 1;
    }

    static boolean shouldSkipAdjustLimit(TreeFilter treeFilter, String repoKeyToInclude) {
        return !treeBrowserImprovedShowRepoInTreeEnabled.getBoolean()
                || treeFilter.getContinueIndex() != 0
                || StringUtils.isBlank(repoKeyToInclude)
                || repoKeyToInclude.equals(SUPPORT_BUNDLE_REPO_KEY)
                || repoKeyToInclude.equals(TRASH_REPO_KEY)
                || repoKeyToInclude.equals(RELEASE_BUNDLE_REPO_KEY);
    }

    public ContinueResult<RepositoryNode> fetchItems() {
        log.debug("Fetching repository items");
        TreeFilter nextTreeFilter = new TreeFilter(treeFilter);
        if (StringUtils.isNotBlank(nextContinueState)) {
            nextTreeFilter.setContinueIndex(Long.parseLong(nextContinueState));
        }
        ContinueResult<RepositoryNode> result = getPagingFromMultipleFunctions(nextTreeFilter, getFetchFunctions());
        sortUpReleaseBundleV2Repos(result);
        sortUpAppVersionsRepos(result);
        log.debug("{} repositories fetched. continue state was set to: '{}'",
                result.getData().size(), result.getContinueState());
        nextContinueState = result.getContinueState();
        return result;
    }

    @VisibleForTesting
    static void sortUpReleaseBundleV2Repos(@NonNull ContinueResult<RepositoryNode> result) {
        List<RepositoryNode> data = result.getData();
        List<RepositoryNode> filteredRepos = data.stream().filter(RootFetchStrategy::isReleaseBundleV2Repo).toList();
        // Keep the default RBv2 repository on the top.
        List<RepositoryNode> releaseBundlesV2Sorted = filteredRepos.stream().sorted((item1, item2) -> {
            if (DEFAULT_RBV2_REPO.equals(item1.getRepoKey())) {return -1;}
            if (DEFAULT_RBV2_REPO.equals(item2.getRepoKey())) {return 1;}
            return 0;
        }).toList();
        data.removeAll(releaseBundlesV2Sorted);
        // RBv2 repositories go after RBv1.
        int targetPosition = 0;
        for (int index = 0; index < data.size(); index++) {
            if (isReleaseBundleV1(data.get(index))) {
                targetPosition = index + 1;
            } else if (targetPosition != 0) {
                break;
            }
        }
        data.addAll(targetPosition, releaseBundlesV2Sorted);
    }

    @VisibleForTesting
    static void sortUpAppVersionsRepos(@NonNull ContinueResult<RepositoryNode> result) {
        List<RepositoryNode> data = result.getData();
        List<RepositoryNode> filteredRepos = data.stream().filter(RootFetchStrategy::isAppVersionsRepo).toList();
        data.removeAll(filteredRepos);
        data.addAll(0, filteredRepos);
    }

    private static boolean isReleaseBundleV1(@NonNull RepositoryNode repositoryNode) {
        return RELEASE_BUNDLE_REPO.equals(repositoryNode.getRepoType());
    }

    private static boolean isReleaseBundleV2Repo(@NonNull RepositoryNode repositoryNode) {
        return repositoryNode.getRepoKey().contains(DEFAULT_RBV2_REPO);
    }

    private static boolean isAppVersionsRepo(@NonNull RepositoryNode repositoryNode) {
        return repositoryNode.getRepoKey().contains(APP_VERSIONS_REPO);
    }

    protected List<FetchFunction<RepositoryNode>> getFetchFunctions() {
        return new ArrayList<>(Arrays.asList(new FetchFunction<>(fetchService::getReleaseBundlesRepoNodes,
                        (long) repositoryConfigService.getRepoConfigKeysByType(Set.of(RELEASE_BUNDLE)).size()),
                new FetchFunction<>(fetchService::getTrashRepo),
                new FetchFunction<>(fetchService::getSupportBundles)));
    }

    List<RepositoryNode> getNodes(List<RepoDescriptor> repos) {
        List<RepositoryNode> items = Lists.newArrayListWithCapacity(repos.size());
        repos.forEach(repo -> {
            String repoType = repo.getKey().endsWith(REMOTE_CACHE_SUFFIX) ? "cached"
                    : RepositoryType.byConfig(repo).getTypeNameLoweCase();
            if (repo instanceof VirtualRepoDescriptor) {
                items.add(fromVirtualDescriptor((VirtualRepoDescriptor) repo));
            } else if (repo instanceof RemoteRepoDescriptor) {
                items.add(fromRemoteDescriptor((RemoteRepoDescriptor) repo));
            } else {
                items.add(new RepositoryNode(repo.getKey(), repo.getType(), repoType));
            }
        });
        return items;
    }

    List<RepositoryNode> getNodesFromRepoConfigs(List<RepoConfig> repos) {
        List<RepositoryNode> items = Lists.newArrayListWithCapacity(repos.size());
        repos.forEach(repo -> {
            String repoType = repo.getKey().endsWith(REMOTE_CACHE_SUFFIX) ? "cached"
                    : RepositoryType.byConfig(repo).getTypeNameLoweCase();
            if (repo instanceof VirtualRepoConfig) {
                items.add(fromVirtualRepoConfig((VirtualRepoConfig) repo));
            } else if (repo instanceof RemoteRepoConfig) {
                items.add(fromRemoteRepoConfig(repo));
            } else {
                items.add(new RepositoryNode(repo.getKey(), repo.getPackageType(), repoType));
            }
        });
        return items;
    }

    protected boolean isDefaultProjectContext() {
        return defaultContext;
    }

    static class RepoComparator implements Comparator<RepoDescriptor> {

        @Override
        public int compare(RepoDescriptor descriptor1, RepoDescriptor descriptor2) {

            //Local repositories can be either ordinary or caches
            if (descriptor1 instanceof LocalRepoDescriptor) {
                boolean repo1IsCache = ((LocalRepoDescriptor) descriptor1).isCache();
                boolean repo2IsCache = ((LocalRepoDescriptor) descriptor2).isCache();

                //Cache repositories should appear in a higher priority
                if (repo1IsCache && !repo2IsCache) {
                    return 1;
                } else if (!repo1IsCache && repo2IsCache) {
                    return -1;
                }
            }
            return descriptor1.getKey().compareTo(descriptor2.getKey());
        }
    }

    static class RepoConfigComparator implements Comparator<RepoConfig> {

        @Override
        public int compare(RepoConfig config, RepoConfig descriptor2) {
            //Local repositories can be either ordinary or caches
            if (config instanceof LocalRepoConfig) {
                boolean repo1IsCache = ((LocalRepoConfig) config).isCache();
                boolean repo2IsCache = ((LocalRepoConfig) descriptor2).isCache();
                //Cache repositories should appear in a higher priority
                if (repo1IsCache && !repo2IsCache) {
                    return 1;
                } else if (!repo1IsCache && repo2IsCache) {
                    return -1;
                }
            }
            return config.getKey().compareTo(descriptor2.getKey());
        }
    }
}
