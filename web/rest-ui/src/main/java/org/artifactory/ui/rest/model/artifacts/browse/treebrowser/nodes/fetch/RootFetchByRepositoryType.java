package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch;

import com.google.common.collect.Iterables;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.TreeFilter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.RepositoryNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.VirtualRemoteRepositoryNode;
import org.artifactory.util.CollectionUtils;
import org.glassfish.jersey.internal.guava.Lists;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.commons.continues.ContinueResult;
import org.jfrog.commons.continues.FetchFunction;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.repo.RepoPath.REMOTE_CACHE_SUFFIX;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType.CACHED;
import static org.jfrog.commons.continues.util.ContinueResultIndexUtils.getLastIndex;

/**
 * @author Omri Ziv
 */
@Slf4j
public class RootFetchByRepositoryType extends RootFetchStrategy {

    private final Collection<RepositoryType> repoOrder;
    private final EnumMap<RepositoryType, FetchFunction<RepositoryNode>> repoDetailsTypeFetchFunctionMap;

    public RootFetchByRepositoryType(TreeFilter treeFilter, Collection<RepositoryType> repoOrder) {
        super(treeFilter);
        this.repoDetailsTypeFetchFunctionMap = buildMap();
        this.repoOrder = repoOrder;
    }

    private EnumMap<RepositoryType, FetchFunction<RepositoryNode>> buildMap() {
        Function<String, Boolean> projectFilter = repositoryService.createProjectReposFilter(projectKey);
        EnumMap<RepositoryType, FetchFunction<RepositoryNode>> map = new EnumMap<>(RepositoryType.class);
        map.put(RepositoryType.LOCAL, new FetchFunction<>(this::getLocalRepoNodes,
                (long) repositoryService.getLocalRepoConfigsIncludingInfoReposByProject(projectKey).size()));
        map.put(RepositoryType.FEDERATED, new FetchFunction<>(this::getFederatedRepoNodes,
                (long) repositoryConfigService.getRepoConfigsByProjectAndType(projectFilter, Set.of(FEDERATED))
                        .size()));
        map.put(CACHED, new FetchFunction<>(this::getCachedRepoNodes,
                (long) repositoryService.getCachedRepoDescriptorsByProject(projectKey).size()));
        map.put(RepositoryType.REMOTE, new FetchFunction<>(this::getRemoteRepoNodes,
                (long) repositoryConfigService.getRepoConfigsByProjectAndType(projectFilter, Set.of(REMOTE)).size()));
        map.put(RepositoryType.VIRTUAL, new FetchFunction<>(this::getVirtualRepoNodes,
                repositoryConfigService.getRepoConfigsByProjectAndType(projectFilter, Set.of(VIRTUAL))
                        .stream()
                        .filter(virtualRepoVisibility)
                        .count()));

        return map;
    }

    @Override
    protected List<FetchFunction<RepositoryNode>> getFetchFunctions() {
        List<FetchFunction<RepositoryNode>> superFunctions = super.getFetchFunctions();
        List<RepositoryType> repositoryTypes = treeFilter.getRepositoryTypes();
        boolean emptyRepos = CollectionUtils.isNullOrEmpty(repositoryTypes);
        List<FetchFunction<RepositoryNode>> fetchFunctions = StreamSupportUtils.stream(this.repoOrder)
                .filter(repoType -> emptyRepos || repositoryTypes.contains(repoType))
                .map(repoDetailsTypeFetchFunctionMap::get)
                .toList();

        List<FetchFunction<RepositoryNode>> allFunctions =
                Lists.newArrayList(Iterables.concat(superFunctions, fetchFunctions));
        log.debug("{} Candidates Fetch Function", allFunctions.size());
        return allFunctions;
    }

    private ContinueResult<RepositoryNode> getLocalRepoNodes(Integer skip, Integer limit) {
        List<LocalConfig> repoConfigs =
                repositoryService.getLocalRepoConfigsIncludingInfoReposByProject(projectKey);
        List<LocalConfig> originalSortedLocalRepos = sortAndGetRepoConfigs(repoConfigs);
        Integer adjustedLimit = adjustLimitInCaseShowRepoInTree(treeFilter, originalSortedLocalRepos, limit);
        List<LocalConfig> localRepoConfigs =
                StreamSupportUtils.stream(originalSortedLocalRepos)
                        .skip(skip)
                        .filter(repo -> descriptorFilter.accept(repo, fetchService.getPermissions()))
                        .limit(adjustedLimit)
                        .toList();
        int lastIndex = getLastIndex(originalSortedLocalRepos, localRepoConfigs, adjustedLimit);
        return new ContinueResult<>(lastIndex, getLocalRepoConfigNodes(localRepoConfigs));
    }

    private ContinueResult<RepositoryNode> getFederatedRepoNodes(Integer skip, Integer limit) {
        Function<String, Boolean> projectFilter = repositoryService.createProjectReposFilter(projectKey);
        List<RepoConfig> repoConfigs =
                repositoryConfigService.getRepoConfigsByProjectAndType(projectFilter, Set.of(FEDERATED));
        List<RepoConfig> originalSortedFederatedRepos = sortAndGetRepoConfigs(repoConfigs);
        Integer adjustedLimit = adjustLimitInCaseShowRepoInTree(treeFilter, originalSortedFederatedRepos, limit);
        List<RepoConfig> federatedRepoConfigs =
                StreamSupportUtils.stream(originalSortedFederatedRepos)
                        .skip(skip)
                        .filter(repo -> descriptorFilter.accept(repo, fetchService.getPermissions()))
                        .limit(adjustedLimit)
                        .toList();
        int lastIndex = getLastIndex(originalSortedFederatedRepos, federatedRepoConfigs, adjustedLimit);
        return new ContinueResult<>(lastIndex, getFederatedNodes(federatedRepoConfigs));
    }

    private ContinueResult<RepositoryNode> getCachedRepoNodes(Integer skip, Integer limit) {
        List<LocalCacheRepoDescriptor> originalSortedLocalRepos =
                sortAndGet(repositoryService.getCachedRepoDescriptorsByProject(projectKey));
        Integer adjustedLimit = adjustLimitInCaseShowRepoInTree(treeFilter, originalSortedLocalRepos, limit);
        List<LocalCacheRepoDescriptor> localCachedRepoDescriptors =
                StreamSupportUtils.stream(originalSortedLocalRepos)
                        .skip(skip)
                        .filter(repo -> descriptorFilter.accept(repo, fetchService.getPermissions()))
                        .limit(adjustedLimit)
                        .toList();
        int lastIndex = getLastIndex(originalSortedLocalRepos, localCachedRepoDescriptors, adjustedLimit);
        return new ContinueResult<>(lastIndex, getLocalNodes(localCachedRepoDescriptors));
    }

    private ContinueResult<RepositoryNode> getRemoteRepoNodes(Integer skip, Integer limit) {
        Function<String, Boolean> projectFilter = repositoryService.createProjectReposFilter(projectKey);
        List<RepoConfig> repoConfigs =
                repositoryConfigService.getRepoConfigsByProjectAndType(projectFilter, Set.of(REMOTE));
        List<RepoConfig> originalSortedRemoteRepos = sortAndGetRepoConfigs(repoConfigs);
        Integer adjustedLimit = adjustLimitInCaseShowRepoInTree(treeFilter, originalSortedRemoteRepos, limit);
        List<RepoConfig> remoteRepoConfigs =
                StreamSupportUtils.stream(originalSortedRemoteRepos)
                        .skip(skip)
                        .filter(repo -> descriptorFilter.accept(repo, fetchService.getPermissions()))
                        .limit(adjustedLimit)
                        .toList();
        int lastIndex = getLastIndex(originalSortedRemoteRepos, remoteRepoConfigs, adjustedLimit);
        return new ContinueResult<>(lastIndex, getRemoteNodes(remoteRepoConfigs));
    }

    /**
     * add virtual repo nodes to repo list
     */
    private ContinueResult<RepositoryNode> getVirtualRepoNodes(Integer skip, Integer limit) {
        Function<String, Boolean> projectFilter = repositoryService.createProjectReposFilter(projectKey);
        List<RepoConfig> repoConfigs = repositoryConfigService
                .getRepoConfigsByProjectAndType(projectFilter, Set.of(VIRTUAL))
                .stream()
                .filter(virtualRepoVisibility)
                .toList();
        List<RepoConfig> originalSortedVirtualRepos = sortAndGetRepoConfigs(repoConfigs);
        Integer adjustedLimit = adjustLimitInCaseShowRepoInTree(treeFilter, originalSortedVirtualRepos, limit);
        List<RepoConfig> virtualRepoConfigs = StreamSupportUtils.stream(originalSortedVirtualRepos)
                .skip(skip)
                .filter(repo -> descriptorFilter.accept(repo, fetchService.getPermissions()))
                .limit(adjustedLimit)
                .toList();
        int lastIndex = getLastIndex(originalSortedVirtualRepos, virtualRepoConfigs, adjustedLimit);
        return new ContinueResult<>(lastIndex, getVirtualNodes(virtualRepoConfigs));
    }

    private List<RepositoryNode> getLocalNodes(List<? extends LocalRepoDescriptor> repos) {
        return StreamSupportUtils.stream(repos)
                .map(repo -> {
                    String repoType =
                            repo.getKey().endsWith(REMOTE_CACHE_SUFFIX) ? CACHED.getTypeNameLoweCase() :
                                    LOCAL.typeNameLowercase();
                    return new RepositoryNode(repo.getKey(), repo.getType(), repoType);
                })
                .toList();
    }

    private List<RepositoryNode> getLocalRepoConfigNodes(List<? extends RepoConfig> repos) {
        return StreamSupportUtils.stream(repos)
                .map(repo -> {
                    String repoType =
                            repo.getKey().endsWith(REMOTE_CACHE_SUFFIX) ? CACHED.getTypeNameLoweCase() :
                                    LOCAL.typeNameLowercase();
                    return new RepositoryNode(repo.getKey(), repo.getPackageType(), repoType);
                })
                .toList();
    }

    private List<RepositoryNode> getRemoteNodes(List<RepoConfig> repos) {
        return StreamSupportUtils.stream(repos)
                .map(VirtualRemoteRepositoryNode::fromRemoteRepoConfig)
                .collect(Collectors.toList());
    }

    private List<RepositoryNode> getVirtualNodes(List<RepoConfig> repos) {
        return StreamSupportUtils.stream(repos)
                .map(VirtualRepoConfig.class::cast)
                .map(VirtualRemoteRepositoryNode::fromVirtualRepoConfig)
                .collect(Collectors.toList());
    }

    private List<RepositoryNode> getFederatedNodes(List<RepoConfig> repos) {
        return StreamSupportUtils.stream(repos)
                .map(repo -> new RepositoryNode(repo.getKey(), repo.getPackageType(), FEDERATED.typeNameLowercase()))
                .toList();
    }
}