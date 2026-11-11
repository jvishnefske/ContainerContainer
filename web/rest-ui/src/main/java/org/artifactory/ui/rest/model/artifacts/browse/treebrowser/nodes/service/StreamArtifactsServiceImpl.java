package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.ItemInfo;
import org.artifactory.mime.MimeType;
import org.artifactory.mime.NamingUtils;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.NestedRealRepositoriesWithRoutes;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.remote.browse.RemoteItem;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.stream.ConcatenatedStream;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.ArtifactFilter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.BaseNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.FileNode;
import org.artifactory.ui.utils.BrowseUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.treeBrowserEnforceReadPermissionOnRemoteListings;
import static org.artifactory.common.ConstantValues.treeBrowserMaxNumberOfLocalsToBrowseIndividually;
import static org.artifactory.repo.NestedRealRepositoriesWithRoutes.resolveNestedRepositories;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service.TreeBrowserUtils.buildRemoteItem;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service.TreeBrowserUtils.buildVirtualItem;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service.TreeBrowserUtils.createRemoteFolderNode;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service.TreeBrowserUtils.getChildItem;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service.TreeBrowserUtils.getRemoteChildItem;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service.TreeBrowserUtils.virtualRepoAccepts;

/**
 * This class implements the StreamArtifactsService interface.
 * It provides methods to get streams of nodes from local, remote, and virtual repositories.
 *
 * @author Hussein Alshqerat
 */
@Slf4j
@AllArgsConstructor
@Component
public class StreamArtifactsServiceImpl implements StreamArtifactsService {

    private final RepositoryService repositoryService;
    private final InternalRepositoryService internalRepositoryService;
    private final AuthorizationService authorizationService;

    @Nonnull
    @Override
    public Stream<BaseNode> getNodesStreamFromLocal(@Nonnull ArtifactFilter artifactFilter) {
        Stream<ItemInfo> localNodes = getLocalNodesStream(artifactFilter);
        Stream<ItemInfo> filteredNodes = getFilteredNodesStream(localNodes, artifactFilter);
        return buildLocalBaseNodeStream(filteredNodes, artifactFilter);
    }

    private Stream<ItemInfo> getLocalNodesStream(ArtifactFilter artifactFilter) {
        RepoPath repoPath = RepoPathFactory.create(artifactFilter.getRepoKey(), artifactFilter.getPath());
        return repositoryService.getChildrenStream(repoPath);
    }

    private Stream<ItemInfo> getFilteredNodesStream(Stream<ItemInfo> localNodes, ArtifactFilter artifactFilter) {
        RepoPath repoPath = RepoPathFactory.create(artifactFilter.getRepoKey(), artifactFilter.getPath());
        Stream<ItemInfo> nodes = localNodes.filter(node -> canRead(node.getRepoPath()));
        boolean canManage = authorizationService.canManage(repoPath);
        return BrowseUtils.filterStreamItemsIfNeeded(nodes, artifactFilter.getRepoPkgType(), canManage)
                .filter(node -> repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(
                        node.getRepoPath()));
    }

    private Stream<BaseNode> buildLocalBaseNodeStream(Stream<ItemInfo> filteredNodesStream,
            ArtifactFilter artifactFilter) {
        return filteredNodesStream.map(
                pathItem -> getChildItem(pathItem, pathItem.getRelPath(), artifactFilter.getRepoType(),
                        artifactFilter.getRepoPkgType(), pathItem.getRepoPath()));
    }

    @Nonnull
    @Override
    public Stream<BaseNode> getNodesStreamFromRemote(@Nonnull ArtifactFilter artifactFilter) {
        String repoKey = artifactFilter.getRepoKey();
        String relativePath = artifactFilter.getPath();
        RepoPath repoPath = InternalRepoPathFactory.create(repoKey, relativePath, artifactFilter.isFolder());
        RemoteRepo<?, ?> remoteRepo = internalRepositoryService.remoteRepositoryByKey(repoKey);
        if (remoteRepo == null) {
            log.warn("Remote repository '{}' not found, skipping tree browse", repoKey);
            return Stream.empty();
        }
        boolean includeRemoteResources = remoteRepo.isListRemoteFolderItems() && remoteRepo.accepts(repoPath);
        Stream<BaseNode> remoteNodes = Stream.empty();
        Stream<BaseNode> cachedNodes = Stream.empty();
        if (includeRemoteResources) {
            remoteNodes = getRemoteNodesStream(artifactFilter, remoteRepo);
        }
        if (remoteRepo.isStoreArtifactsLocally() && !remoteRepo.isBlackedOut()) {
            cachedNodes = getRemoteCachedNodesStream(artifactFilter, remoteRepo);
        }
        return Stream.concat(cachedNodes, remoteNodes);
    }

    @VisibleForTesting
    Stream<BaseNode> getRemoteNodesStream(ArtifactFilter artifactFilter, RemoteRepo<?, ?> remoteRepo) {
        String relativePath = artifactFilter.getPath();
        try {
            return fetchAndFilterRemoteResourcesStream(artifactFilter, remoteRepo, relativePath);
        } catch (Exception e) {
            RepoPath repoPath = RepoPathFactory.create(remoteRepo.getKey(), relativePath);
            log.warn("Failed to list remote resources for '{}'. Returning empty stream", repoPath, e);
            return Stream.empty();
        }
    }

    @Nonnull
    private Stream<BaseNode> fetchAndFilterRemoteResourcesStream(ArtifactFilter artifactFilter,
            RemoteRepo<?, ?> remoteRepo,
            String relativePath) {
        List<RemoteItem> remoteItems = remoteRepo.listRemoteResources(relativePath);
        return remoteItems.stream().map(node -> buildRemoteItem(node, remoteRepo, relativePath))
                .filter(BrowseUtils::filterChecksums)
                .filter(node -> remoteRepo.accepts(node.getRepoPath()))
                .filter(node -> isReadPermitted(node.getRepoPath()))
                .map(node -> getRemoteChildItem(node, "remote", artifactFilter.getRepoPkgType()));
    }

    private Stream<BaseNode> getRemoteCachedNodesStream(ArtifactFilter artifactFilter, RemoteRepo<?, ?> remoteRepo) {
        LocalCacheRepo remoteCacheRepo = remoteRepo.getLocalCacheRepo();
        String relativePath = artifactFilter.getPath();
        RepoPath cacheRepoPath = InternalRepoPathFactory.create(remoteCacheRepo.getKey(), relativePath,
                artifactFilter.isFolder());
        if (remoteRepo.isBlackedOut() || !remoteRepo.accepts(cacheRepoPath)) {
            return Stream.empty();
        }
        return getRemoteCachedNodesFilterdStream(artifactFilter, remoteRepo, remoteCacheRepo);
    }

    private Stream<BaseNode> getRemoteCachedNodesFilterdStream(ArtifactFilter artifactFilter,
            RemoteRepo<?, ?> remoteRepo, LocalCacheRepo remoteCacheRepo) {
        String relativePath = artifactFilter.getPath();
        RepoPath repoPath = RepoPathFactory.create(remoteCacheRepo.getKey(), relativePath);
        Stream<ItemInfo> nodes = repositoryService.getChildrenStream(repoPath);
        ArtifactFilter remoteCacheArtifactFilter = new ArtifactFilter(artifactFilter);
        remoteCacheArtifactFilter.setRepoType("local");
        remoteCacheArtifactFilter.setRepoKey(remoteCacheRepo.getKey());
        Stream<ItemInfo> filteredNodes = getFilteredNodesStream(nodes, remoteCacheArtifactFilter)
                .filter(node -> remoteRepo.accepts(node.getRepoPath()));
        return buildeRemoteBaseNodesStream(artifactFilter, remoteCacheRepo, filteredNodes, relativePath);
    }

    private Stream<BaseNode> buildeRemoteBaseNodesStream(ArtifactFilter artifactFilter, LocalCacheRepo remoteCacheRepo,
            Stream<ItemInfo> nodes, String relativePath) {
        return nodes.map(node -> {
            if (node.isFolder()) {
                return createRemoteFolderNode(node, "remote");
            } else {
                MimeType mimeType = NamingUtils.getMimeType(relativePath);
                RepoPath localItemRepoPath =
                        RepoPathFactory.create(remoteCacheRepo.getKey(), relativePath + "/" + node.getName());
                return new FileNode(localItemRepoPath, node.getName(), artifactFilter.getRepoType(),
                        RepoType.fromType(artifactFilter.getRepoPkgType()), mimeType.isArchive());
            }
        });
    }

    @Nonnull
    @Override
    public Stream<BaseNode> getNodesStreamFromVirtual(@Nonnull ArtifactFilter artifactFilter) {
        String repoPath = artifactFilter.getPath();
        String virtualRepoKey = artifactFilter.getRepoKey();
        VirtualRepo virtualRepo = internalRepositoryService.virtualRepositoryByKey(virtualRepoKey);
        NestedRealRepositoriesWithRoutes realRepositoriesRoutes =
                resolveNestedRepositories(virtualRepo, repoPath, new HashSet<>(), new HashMap<>());
        if (realRepositoriesRoutes == null || realRepositoriesRoutes.getPaths().isEmpty()) {
            return Stream.empty();
        }

        return getVirtulaBaseNodesStream(artifactFilter, virtualRepo, realRepositoriesRoutes);
    }

    /**
     * This method is used to get a stream of BaseNode objects from a virtual repository.
     * It first resolves the nested repositories within the virtual repository.
     * If no nested repositories are found or the paths are empty, it returns an empty stream.
     * Otherwise, it gets the streams of nodes from the local and remote repositories,
     * the stream of local nodes depends on the number of local repositories and the max number of locals to browse
     * individually.
     * if the number of locals is greater than the max number of locals to browse individually,
     * it gets the nodes from all locals ignoring the local repositories keys.
     * concatenates them, and filters the nodes based on the routes and include/exclude patterns of the virtual
     * repository.
     *
     * @param artifactFilter         The artifact filter containing the repository key and path.
     * @param virtualRepo            The virtual repository from which to get the nodes.
     * @param realRepositoriesRoutes The nested real repositories within the virtual repository and their routes.
     * @return A stream of BaseNode objects from the virtual repository.
     */
    private Stream<BaseNode> getVirtulaBaseNodesStream(ArtifactFilter artifactFilter, VirtualRepo virtualRepo,
            NestedRealRepositoriesWithRoutes realRepositoriesRoutes) {
        List<String> localReposKeys = realRepositoriesRoutes.getLocalReposKeys();
        List<String> remoteReposKeys = realRepositoriesRoutes.getRemoteReposKeys();
        List<Supplier<Stream<BaseNode>>> localsStreamSuppliers =
                getLocalsStreamSuppliers(artifactFilter, localReposKeys);
        List<Supplier<Stream<BaseNode>>> remotesStreamSuppliers =
                getRemotesStreamSuppliers(artifactFilter, remoteReposKeys);
        List<Supplier<Stream<BaseNode>>> streamSuppliers = new ArrayList<>();
        streamSuppliers.addAll(localsStreamSuppliers);
        streamSuppliers.addAll(remotesStreamSuppliers);
        return ConcatenatedStream.of(streamSuppliers)
                .filter(node -> isNodeAcceptedByRoutes(
                        realRepositoriesRoutes.getRoutesByRepoKey(node.getRepoKey()), node))
                .filter(node -> checkIncludeExcludePatterns(virtualRepo, node.getRepoPath()))
                .map(node -> buildVirtualItem(node, virtualRepo.getKey()));
    }

    private List<Supplier<Stream<BaseNode>>> getLocalsStreamSuppliers(ArtifactFilter artifactFilter,
            List<String> localReposKeys) {
        List<Supplier<Stream<BaseNode>>> localsStreamSuppliers;
        int maxNumberOfLocalsToBrowseIndividually = treeBrowserMaxNumberOfLocalsToBrowseIndividually.getInt();
        if (maxNumberOfLocalsToBrowseIndividually != -1 &&
                localReposKeys.size() >= maxNumberOfLocalsToBrowseIndividually) {
            localsStreamSuppliers = List.of(() -> getNodesStreamFromLocalIgnoringLocalReposKeys(artifactFilter)
                    .filter(node -> localReposKeys.contains(node.getRepoKey())));
        } else {
            localsStreamSuppliers = localReposKeys.stream()
                    .map(localRepoKey -> createRepoStreamSupplier(localRepoKey, "local", artifactFilter)).toList();
        }
        return localsStreamSuppliers;
    }

    private List<Supplier<Stream<BaseNode>>> getRemotesStreamSuppliers(ArtifactFilter artifactFilter,
            List<String> remoteReposKeys) {
        return remoteReposKeys.stream()
                .map(remoteRepoKey -> createRepoStreamSupplier(remoteRepoKey, "remote", artifactFilter)).toList();
    }

    private Supplier<Stream<BaseNode>> createRepoStreamSupplier(String repoKey, String repoType,
            ArtifactFilter artifactFilter) {
        return () -> {
            ArtifactFilter artifactFilterClone = new ArtifactFilter(artifactFilter);
            artifactFilterClone.setRepoKey(repoKey);
            artifactFilterClone.setRepoType(repoType);
            artifactFilterClone.setRepoPkgType(artifactFilter.getRepoPkgType());
            if ("local".equals(repoType)) {
                return getNodesStreamFromLocal(artifactFilterClone);
            } else {
                return getNodesStreamFromRemote(artifactFilterClone);
            }
        };
    }

    private Stream<BaseNode> getNodesStreamFromLocalIgnoringLocalReposKeys(ArtifactFilter artifactFilter) {
        RepoPath repoPath = RepoPathFactory.create(artifactFilter.getRepoKey(), artifactFilter.getPath());
        Stream<ItemInfo> items = repositoryService.getNodesStreamIgnoringRepo(repoPath);
        Stream<ItemInfo> filteredItems = getFilteredNodesStream(items, artifactFilter);
        return buildLocalBaseNodeStream(filteredItems, artifactFilter);
    }

    private boolean canRead(RepoPath childRepoPath) {
        return authorizationService.canRead(childRepoPath);
    }

    private boolean isReadPermitted(RepoPath repoPath) {
        return !treeBrowserEnforceReadPermissionOnRemoteListings.getBoolean() || canRead(repoPath);
    }

    private boolean isNodeAcceptedByRoutes(List<List<VirtualRepo>> pathFor, BaseNode node) {
        if (pathFor.isEmpty()) {
            return true;
        }
        for (List<VirtualRepo> list : pathFor) {
            boolean allAccepted = true;
            for (VirtualRepo repo : list) {
                if (!repo.isPathPatternValid(node.getRepoPath(), node.getPath())) {
                    allAccepted = false;
                    break;
                }
            }
            if (allAccepted) {
                return true;
            }
        }
        return false;
    }

    private boolean checkIncludeExcludePatterns(VirtualRepo repo, RepoPath path) {
        return virtualRepoAccepts(repo, path);
    }
}