package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.repo.BaseBrowsableItem;
import org.artifactory.api.repo.BrowsableItemCriteria;
import org.artifactory.api.repo.RepositoryBrowsingService;
import org.artifactory.api.repo.VirtualRepoItem;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.mime.MimeType;
import org.artifactory.mime.NamingUtils;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.remote.browse.RemoteItem;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.threadlocal.cache.ReposItemsThreadLocalCacheService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.ArtifactFilter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.BaseNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.FileNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.FolderNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.NodeComparator;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.NodeRepoTypeHelper;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.archive.ArchiveEntriesTree;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.ArtifactsFetchStrategy;
import org.jfrog.commons.continues.ContinueResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.common.ConstantValues.displayIconsOnLocalReposEnabled;
import static org.artifactory.common.ConstantValues.displayIconsOnVirtualReposEnabled;
import static org.artifactory.common.ConstantValues.treeBrowserStreamSizeLimit;
import static org.artifactory.common.ConstantValues.treeBrowserEnforceReadPermissionOnRemoteListings;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service.TreeBrowserUtils.buildRemoteItem;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service.TreeBrowserUtils.getChildItem;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service.TreeBrowserUtils.getRemoteChildItem;
import static org.artifactory.ui.rest.model.utils.bundle.TreeBrowserBundleUtils.removeHiddenReleaseBundleNodes;
import static org.artifactory.ui.rest.model.utils.evidence.EvidenceUtils.removeEvidenceNodes;
import static org.artifactory.ui.utils.BrowseUtils.filterChecksums;
import static org.artifactory.ui.utils.BrowseUtils.filterInternalMetadata;
import static org.artifactory.ui.utils.BrowseUtils.filterItemsIfNeeded;
import static org.jfrog.commons.continues.util.PagingUtils.getPagingFromMultipleFunctions;

/**
 * @author Hussein Alshqerat
 */
@Slf4j
@Service
public class ArtifactFetchServiceImpl implements ArtifactFetchService {

    private static final int CONTINUE_STATE_NO_MORE_ITEMS = -1;
    private static final int CONTINUE_STATE_REACHED_LIMIT = -2;
    private final NodeRepoTypeHelper nodeRepoTypeHelper;
    private final RepositoryBrowsingService repositoryBrowsingService;
    private final AuthorizationService authorizationService;
    private final StreamArtifactsService streamArtifactsService;
    private final NodeComparator nodeComparator;
    private final InternalRepositoryService repositoryService;

    public ArtifactFetchServiceImpl(RepositoryBrowsingService repositoryBrowsingService,
            AuthorizationService authorizationService, AddonsManager addonsManager,
            StreamArtifactsService streamArtifactsService, PropertiesService propertiesService,
            InternalRepositoryService repositoryService, PackageSpecification packageSpecification) {
        this.repositoryBrowsingService = repositoryBrowsingService;
        this.streamArtifactsService = streamArtifactsService;
        this.authorizationService = authorizationService;
        this.repositoryService = repositoryService;
        this.nodeRepoTypeHelper = new NodeRepoTypeHelper(addonsManager, repositoryService, propertiesService,
                packageSpecification);
        this.nodeComparator = new NodeComparator();
    }

    @Nonnull
    @Override
    public ContinueResult<? extends RestModel> fetchItems(ArtifactsFetchStrategy artifactsFetchStrategy,
            ArtifactFilter artifactFilter, boolean isCompact, boolean isArchiveRequest) {
        log.debug("Fetching artifacts under the path {}.", artifactFilter.getPath());
        if (isArchiveRequest) {
            return new ArchiveEntriesTree().buildChildren(artifactFilter.getRepoKey(), artifactFilter.getPath());
        }
        List<BaseNode> nodes = getChildren(artifactFilter);
        ContinueResult<BaseNode> result =
                getPagingFromMultipleFunctions(artifactFilter, artifactsFetchStrategy.getFetchFunction(nodes));


        List<BaseNode> filteredNodes = removeEvidenceNodes(result.getData());
        filteredNodes = removeHiddenReleaseBundleNodes(filteredNodes);
        result.setData(filteredNodes);

        if (result.getData() != null && isCompact) {
            compactFolders(result.getData());
        }
        setFolderIconsIfNeeded(result.getData());
        if (result.getContinueState() == null || Long.parseLong(result.getContinueState()) == nodes.size()) {
            result.setContinueState("-1");
        }
        log.debug("{} artifacts fetched. continue state was set to: '{}'", result.getData().size(),
                result.getContinueState());
        return result;
    }

    @Nonnull
    @Override
    public ContinueResult<? extends RestModel> fetchItemsStreamBased(ArtifactFilter artifactFilter, boolean isCompact,
            boolean isArchiveRequest) {
        if (isArchiveRequest) {
            log.info("Archive request for artifacts under the path {} from repo {}.",
                    artifactFilter.getPath(), artifactFilter.getRepoKey());
            return new ArchiveEntriesTree().buildChildren(artifactFilter.getRepoKey(), artifactFilter.getPath());
        }
        boolean isShowInTree = isNotBlank(artifactFilter.getMustInclude());

        int maxArtifactsToFetch = isShowInTree ? Math.toIntExact(artifactFilter.getLimit()) :
                (int) (artifactFilter.getContinueIndex() + Math.toIntExact(artifactFilter.getLimit()));
        // we will try to fetch one more item to check if we reached the limit or if there are more items
        return isShowInTree ? getShowInTreeResult(artifactFilter, maxArtifactsToFetch, isCompact) :
                getFetchNodesResult(artifactFilter, maxArtifactsToFetch + 1, isCompact);
    }

    /**
     * @param artifactFilter      - the required data for fetching the artifacts.
     * @param maxArtifactsToFetch The maximum number of artifacts to fetch in the result.
     * @param isCompact           - if true, the empty folders will be compacted.
     *                            in the show in tree request the before items are the items before the candidate which
     *                            is will not be changed if we clicked on load more in the same scope, so we will
     *                            fetch the same items, but the after items will be continued from the last fetched
     *                            items.
     *                            in both cases we add one more item to check if we reached the limit or if there are
     *                            more items
     * @return - the result of the fetch operation.
     */
    private ContinueResult<? extends RestModel> getShowInTreeResult(ArtifactFilter artifactFilter,
            int maxArtifactsToFetch, boolean isCompact) {
        log.debug("Show in tree request for artifact {} under the path {} from repo {}.",
                artifactFilter.getMustInclude(), artifactFilter.getPath(), artifactFilter.getRepoKey());
        int continueState = Math.toIntExact(artifactFilter.getContinueIndex());
        List<BaseNode> result =
                showInTree(artifactFilter, maxArtifactsToFetch + 1, continueState + maxArtifactsToFetch + 1);
        if (isCompact) {
            compactFolders(result);
        }
        setFolderIconsIfNeeded(result);
        return buildShowInTreeContinueResult(result, maxArtifactsToFetch, artifactFilter.getMustInclude(),
                continueState);
    }

    /**
     * Builds a ContinueResult object based on the provided list of nodes and the specified parameters.
     *
     * @param result              The list of BaseNode objects representing the tree structure.
     * @param maxArtifactsToFetch The maximum number of artifacts to fetch in the result.
     * @param mustInclude         The text of the node that must be included in the result.
     * @param continueState       The current continuation state, indicating the position to continue fetching from.
     * @return A ContinueResult object containing the subset of the result list and the updated continuation state.
     */
    private ContinueResult<? extends RestModel> buildShowInTreeContinueResult(
            List<BaseNode> result, int maxArtifactsToFetch, String mustInclude, int continueState) {

        int candidateIndex = findCandidateIndex(result, mustInclude);
        int originResultSize = result.size();
        ContinueResult<? extends RestModel> finalResult;
        if (continueState != 0) {
            // the user clicked on load more after show in tree request in the same scope.
            finalResult =
                    handleContinueState(result, maxArtifactsToFetch, candidateIndex, continueState, originResultSize);
        } else {
            log.debug("Initial fetch for show in tree request.");
            finalResult = handleInitialState(result, maxArtifactsToFetch, candidateIndex, originResultSize);
        }
        if (finalResult.getData().size() > treeBrowserStreamSizeLimit.getInt()) {
            log.debug("Reached the tree browser stream size limit of {}.", treeBrowserStreamSizeLimit.getInt());
            log.debug("Returning the first {} items.", treeBrowserStreamSizeLimit.getInt());
            return new ContinueResult<>(String.valueOf(CONTINUE_STATE_REACHED_LIMIT),
                    finalResult.getData().subList(0, treeBrowserStreamSizeLimit.getInt()));
        }
        return finalResult;
    }

    /**
     * Finds the index of the node that matches the specified text.
     *
     * @param result      The list of BaseNode objects representing the tree structure.
     * @param mustInclude The text of the node that must be included in the result.
     * @return The index of the node that matches the specified text, or -1 if not found.
     */
    private int findCandidateIndex(List<BaseNode> result, String mustInclude) {
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).getText().equals(mustInclude)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Handles the scenario when the continuation state is not zero.
     *
     * @param result              The list of BaseNode objects representing the tree structure.
     * @param maxArtifactsToFetch The maximum number of artifacts to fetch in the result.
     * @param candidateIndex      The index of the node that must be included in the result.
     * @param continueState       The current continuation state.
     * @param originResultSize    The original size of the result list.
     * @return A ContinueResult object containing the subset of the result list and the updated continuation state.
     */
    private ContinueResult<? extends RestModel> handleContinueState(
            List<BaseNode> result, int maxArtifactsToFetch, int candidateIndex, int continueState,
            int originResultSize) {
        int fromIndex = Math.max(0, candidateIndex - maxArtifactsToFetch / 2);
        int toIndex = Math.min(result.size(), (continueState + maxArtifactsToFetch) + fromIndex);

        result = result.subList(fromIndex, toIndex);

        if (result.size() < maxArtifactsToFetch) {
            return new ContinueResult<>(String.valueOf(CONTINUE_STATE_NO_MORE_ITEMS), result);
        }
        if (originResultSize - candidateIndex < maxArtifactsToFetch + continueState) {
            return new ContinueResult<>(String.valueOf(CONTINUE_STATE_NO_MORE_ITEMS), result);
        }

        return new ContinueResult<>(continueState + maxArtifactsToFetch, result);
    }

    /**
     * Handles the scenario when the continuation state is zero, indicating the initial fetch.
     *
     * @param result              The list of BaseNode objects representing the tree structure.
     * @param maxArtifactsToFetch The maximum number of artifacts to fetch in the result.
     * @param candidateIndex      The index of the node that must be included in the result.
     * @param originResultSize    The original size of the result list.
     * @return A ContinueResult object containing the subset of the result list and the updated continuation state.
     */
    private ContinueResult<? extends RestModel> handleInitialState(
            List<BaseNode> result, int maxArtifactsToFetch, int candidateIndex, int originResultSize) {

        int numberOfItemsAfter = originResultSize - candidateIndex - 1;

        if (originResultSize <= maxArtifactsToFetch) {
            log.debug("The number of items in the result {} is less than the maximum number of artifacts to fetch {}, "
                    + "returning all items in the result.", result.size(), maxArtifactsToFetch);
            return new ContinueResult<>(String.valueOf(CONTINUE_STATE_NO_MORE_ITEMS), result);
        }

        return paginateResult(result, maxArtifactsToFetch, candidateIndex, candidateIndex, numberOfItemsAfter);
    }

    /**
     * Paginates the result list based on the position of the candidate index.
     *
     * @param result              The list of BaseNode objects representing the tree structure.
     * @param maxArtifactsToFetch The maximum number of artifacts to fetch in the result.
     * @param candidateIndex      The index of the node that must be included in the result.
     * @param numberOfItemsBefore The number of items before the candidate index.
     * @param numberOfItemsAfter  The number of items after the candidate index.
     * @return A ContinueResult object containing the paginated result list.
     */
    private ContinueResult<? extends RestModel> paginateResult(
            List<BaseNode> result, int maxArtifactsToFetch, int candidateIndex,
            int numberOfItemsBefore, int numberOfItemsAfter) {

        int half = maxArtifactsToFetch / 2;

        if (numberOfItemsBefore < half) {
            // Not enough items before the candidate, fetch from the start
            result = result.subList(0, maxArtifactsToFetch);
        } else if (numberOfItemsAfter < half) {
            // Not enough items after the candidate, fetch from the end
            int startIndex = candidateIndex - (maxArtifactsToFetch - numberOfItemsAfter) + 1;
            result = result.subList(startIndex, result.size());
        } else {
            // Enough items before and after, center around the candidate
            int startIndex = candidateIndex - maxArtifactsToFetch / 2;
            result = result.subList(startIndex, startIndex + maxArtifactsToFetch);
            if (numberOfItemsAfter <= half) {
                return new ContinueResult<>(String.valueOf(CONTINUE_STATE_NO_MORE_ITEMS), result);
            } else {
                return new ContinueResult<>(String.valueOf(result.size()), result);
            }
        }

        if (numberOfItemsAfter < maxArtifactsToFetch) {
            return new ContinueResult<>(String.valueOf(CONTINUE_STATE_NO_MORE_ITEMS), result);
        }

        return new ContinueResult<>(String.valueOf(result.size()), result);
    }

    private ContinueResult<? extends RestModel> getFetchNodesResult(ArtifactFilter artifactFilter,
            int maxArtifactsToFetch,
            boolean isCompact) {
        log.debug("Fetching artifacts under the path {} from {} repo {}.", artifactFilter.getPath(),
                artifactFilter.getRepoType(), artifactFilter.getRepoKey());
        List<BaseNode> result = fetchNodes(artifactFilter, maxArtifactsToFetch);
        if (isCompact) {
            compactFolders(result);
        }
        setFolderIconsIfNeeded(result);
        return buildContinueResult(result, maxArtifactsToFetch);
    }

    private ContinueResult<? extends RestModel> buildContinueResult(List<BaseNode> result, int maxArtifactsToFetch) {
        if (result.size() > treeBrowserStreamSizeLimit.getInt()) {
            return new ContinueResult<>(CONTINUE_STATE_REACHED_LIMIT,
                    result.subList(0, treeBrowserStreamSizeLimit.getInt()));
        }
        if (result.size() < maxArtifactsToFetch) {
            return new ContinueResult<>(CONTINUE_STATE_NO_MORE_ITEMS, result);
        }
        result.remove(result.size() - 1);
        return new ContinueResult<>(result.size(), result);
    }

    private List<BaseNode> showInTree(ArtifactFilter artifactFilter, int before, int after) {
        return switch (artifactFilter.getRepoType()) {
            case "remote" -> showInRemoteTree(artifactFilter, before, after);
            case "virtual" -> showInVirtualTree(artifactFilter, before, after);
            default -> showInLocalTree(artifactFilter, before, after);
        };
    }

    private List<BaseNode> showInVirtualTree(ArtifactFilter artifactFilter, int before, int after) {
        String repoPath = artifactFilter.getPath();
        String virtualRepoKey = artifactFilter.getRepoKey();
        VirtualRepo virtualRepo = repositoryService.virtualRepositoryByKey(virtualRepoKey);
        if (virtualRepo == null) {
            return new ArrayList<>();
        }
        Set<LocalRepo> localRepos = virtualRepo.getResolvedLocalAndFederatedRepos();
        Set<RemoteRepo> remoteRepos = virtualRepo.getResolvedRemoteRepos();
        BaseNode candidate =
                searchForItemInLocalAndCachedRepos(artifactFilter.getMustInclude(), artifactFilter.getPath(),
                        localRepos, remoteRepos);
        if (isNull(candidate)) {
            candidate = searchForItemInRemoteRepos(artifactFilter.getMustInclude(), remoteRepos, repoPath,
                    artifactFilter.getRepoPkgType(), virtualRepoKey);
        }
        if (candidate != null) {
            return getAroundCandidateNodes(artifactFilter, candidate, before, after);
        } else {
            return new ArrayList<>();
        }
    }

    private List<BaseNode> showInRemoteTree(ArtifactFilter artifactFilter, int before, int after) {
        String repoKey = artifactFilter.getRepoKey();
        String relativePath = artifactFilter.getPath();
        RepoPath repoPath = InternalRepoPathFactory.create(repoKey, relativePath);
        RemoteRepo<?, ?> remoteRepo = repositoryService.remoteRepositoryByKey(artifactFilter.getRepoKey());
        BaseNode candidate = null;
        if (!remoteRepo.isBlackedOut() && remoteRepo.isStoreArtifactsLocally()) {
            RepoPath candidateCacheRepoPath =
                    RepoPathFactory.create(requireNonNull(remoteRepo.getLocalCacheRepo()).getKey(),
                            relativePath + "/" + artifactFilter.getMustInclude());
            if (remoteRepo.accepts(candidateCacheRepoPath)) {
                candidate = searchForItemLocally(candidateCacheRepoPath);
                if (candidate != null) {
                    return getAroundCandidateNodes(artifactFilter, candidate, before, after);
                }
            }
        }
        if (remoteRepo.isListRemoteFolderItems()) {
            RepoPath candidateRepoPath =
                    RepoPathFactory.create(remoteRepo.getKey(), relativePath + "/" + artifactFilter.getMustInclude());
            if (remoteRepo.accepts(candidateRepoPath)) {
                candidate = searchForItemRemotely(artifactFilter.getMustInclude(), remoteRepo, repoPath,
                        artifactFilter.getRepoPkgType());
            }
        }
        if (candidate != null) {
            return getAroundCandidateNodes(artifactFilter, candidate, before, after);
        }
        return new ArrayList<>();
    }

    private List<BaseNode> getAroundCandidateNodes(ArtifactFilter artifactFilter, BaseNode candidate,
            int before, int after) {
        PriorityQueue<BaseNode> children = new PriorityQueue<>(nodeComparator.reversed());
        Stream<BaseNode> nodes = getBaseNodeStream(artifactFilter);
        buildSortedNodesPageAroundCandidate(nodes, candidate, children, nodeComparator, before, after);
        return new ArrayList<>(children.stream().sorted(nodeComparator).toList());
    }

    private List<BaseNode> showInLocalTree(ArtifactFilter artifactFilter, int before, int after) {
        RepoPath repoPath = InternalRepoPathFactory.create(artifactFilter.getRepoKey(),
                artifactFilter.getPath() + "/" + artifactFilter.getMustInclude());
        BaseNode candidate = searchForItemLocally(repoPath);
        log.debug("Searching for item {} in local tree.", repoPath.toPath());
        if (candidate == null) {
            log.debug("item {} not found, returning empty list.", repoPath.toPath());
            return new ArrayList<>();
        }
        return getAroundCandidateNodes(artifactFilter, candidate, before, after);
    }

    private BaseNode searchForItemLocally(RepoPath repoPath) {
        try {
            ItemInfo itemInfo = repositoryService.getItemInfo(repoPath);
            if (itemInfo.isFolder()) {
                FolderInfo folderInfo = (FolderInfo) itemInfo;
                return new FolderNode(folderInfo, repoPath.getName(), "local");
            } else {
                MimeType mimeType = NamingUtils.getMimeType(repoPath.getPath());
                return new FileNode(repoPath, repoPath.getName(), "local", RepoType.fromType(repoPath.getRepoKey()),
                        mimeType.isArchive());
            }
        } catch (Exception e) {
            log.debug("Item {} not found in the database", repoPath.getPath());
            return null;
        }
    }

    private BaseNode searchForItemInLocalAndCachedRepos(String artifactName, String path, Set<LocalRepo> localRepos,
            Set<RemoteRepo> remoteRepos) {
        BaseNode localCandidate;
        for (LocalRepo localRepo : localRepos) {
            localCandidate = searchForCandidateInLocalRepo(artifactName, path, localRepo);
            if (localCandidate != null) {
                return localCandidate;
            }
        }
        for (RemoteRepo<?, ?> remoteRepo : remoteRepos) {
            if (isRemoteRepoStoreArtifactsLocally(remoteRepo)) {
                RealRepo<?, ?> remoteCache = requireNonNull(remoteRepo.getLocalCacheRepo());
                localCandidate = searchForCandidateInLocalRepo(artifactName, path, remoteCache);
                if (localCandidate != null) {
                    return localCandidate;
                }
            }
        }
        return null;
    }

    private BaseNode searchForCandidateInLocalRepo(String artifactName, String path, RealRepo<?, ?> realRepo) {
        RepoPath candidateRepoPath = RepoPathFactory.create(realRepo.getKey(), path + "/" + artifactName);
        return searchForItemLocally(candidateRepoPath);
    }

    private boolean isRemoteRepoStoreArtifactsLocally(RealRepo<?, ?> realRepo) {
        return !realRepo.isBlackedOut() && ((RemoteRepo<?, ?>) realRepo).isStoreArtifactsLocally();
    }

    private BaseNode searchForItemInRemoteRepos(String artifactName, Set<RemoteRepo> remoteRepos, String repoPath,
            String repoPkgType, String virtualRepoKey) {
        BaseNode candidate;
        for (RemoteRepo<?, ?> remoteRepo : remoteRepos) {
            candidate = searchForItemRemotely(artifactName, remoteRepo,
                    RepoPathFactory.create(virtualRepoKey, repoPath), repoPkgType);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private BaseNode searchForItemRemotely(String artifactName, RemoteRepo<?, ?> remoteRepo, RepoPath repoPath,
            String repoPkgType) {
        List<RemoteItem> remoteItems =
                remoteRepo.listRemoteResources(remoteRepo.getRepoPath(repoPath.getPath()).getPath());
        Optional<BaseNode> baseNode =
                remoteItems.stream().filter(node -> node.getName().equals(artifactName))
                        .map(node -> buildRemoteItem(node, remoteRepo, repoPath.getPath()))
                        .map(node -> getRemoteChildItem(node, "remote", repoPkgType))
                        .filter(node -> remoteRepo.accepts(node.getRepoPath()))
                        .filter(node -> isReadPermitted(node.getRepoPath()))
                        .findAny();
        return baseNode.orElse(null);
    }

    private boolean isReadPermitted(RepoPath repoPath) {
        return !treeBrowserEnforceReadPermissionOnRemoteListings.getBoolean() || authorizationService.canRead(repoPath);
    }

    private void buildSortedNodesPageAroundCandidate(Stream<BaseNode> nodes, BaseNode candidate,
            PriorityQueue<BaseNode> children, NodeComparator nodeComparator, int beforeQueueSize, int afterQueueSize) {
        PriorityQueue<BaseNode> beforeQueue = new PriorityQueue<>(nodeComparator);
        PriorityQueue<BaseNode> afterQueue = new PriorityQueue<>(nodeComparator.reversed());
        Set<String> nodesNames = new HashSet<>();
        nodes.forEach(
                node -> tryToInsert(candidate, beforeQueue, afterQueue, nodesNames, beforeQueueSize, afterQueueSize,
                        node));
        children.addAll(beforeQueue);
        children.addAll(afterQueue);
    }

    private void tryToInsert(BaseNode candidate, PriorityQueue<BaseNode> beforeQueue,
            PriorityQueue<BaseNode> afterQueue, Set<String> nodesNames, int beforeQueueSize, int afterQueueSize,
            BaseNode node) {
        if (nodesNames.contains(node.getText())) {
            return;
        }
        int comparison = nodeComparator.compare(node, candidate);
        if (comparison >= 0) {
            insertToQueue(afterQueue, nodesNames, afterQueueSize, node, candidate.getText());
        } else {
            insertToQueue(beforeQueue, nodesNames, beforeQueueSize, node, candidate.getText());
        }
    }

    private void insertToQueue(PriorityQueue<BaseNode> queue, Set<String> nodesNames, int queueSize, BaseNode node,
            String text) {
        if (queueSize == 0) {
            return;
        }
        if (queue.add(node)) {
            nodesNames.add(node.getText());
            if (queue.size() > queueSize) {
                if (queue.peek().getText().equals(text)) {
                    nodesNames.remove(requireNonNull(node.getText()));
                    return;
                }
                BaseNode removed = queue.poll();
                nodesNames.remove(requireNonNull(removed).getText());
            }
        }
    }

    private List<BaseNode> fetchNodes(ArtifactFilter artifactFilter, int limit) {
        PriorityQueue<BaseNode> children = new PriorityQueue<>(limit, nodeComparator.reversed());
        Stream<BaseNode> baseNodeStream = getBaseNodeStream(artifactFilter);
        if (artifactFilter.getRepoType().equals("remote") || artifactFilter.getRepoType().equals("virtual")) {
            buildRemoteNodesPageSorted(baseNodeStream, children, limit);
        } else {
            buildSortedNodesPage(baseNodeStream, children, limit);
        }
        return new ArrayList<>(children.stream().sorted(nodeComparator).toList());
    }

    private Stream<BaseNode> getBaseNodeStream(ArtifactFilter artifactFilter) {
        return switch (artifactFilter.getRepoType()) {
            case "remote" -> streamArtifactsService.getNodesStreamFromRemote(artifactFilter);
            case "virtual" -> streamArtifactsService.getNodesStreamFromVirtual(artifactFilter);
            default -> streamArtifactsService.getNodesStreamFromLocal(artifactFilter);
        };
    }

    private void buildSortedNodesPage(Stream<BaseNode> nodes, PriorityQueue<BaseNode> children, int limit) {
        nodes.forEach(node -> {
            if (children.add(node) && children.size() > limit) {
                children.poll();
            }
        });
    }

    /**
     * build sorted remote nodes page around candidate,
     * we create a set of nodes name to avoid duplicates in case items exist in remote and cache.
     *
     * @param remoteNodes - remote nodes
     * @param children    - children
     * @param limit       - limit
     */
    private void buildRemoteNodesPageSorted(Stream<BaseNode> remoteNodes, PriorityQueue<BaseNode> children, int limit) {
        Set<String> nodesNames = new HashSet<>();
        remoteNodes.forEach(node -> {
            if (nodesNames.contains(node.getText())) {
                return;
            }
            if (children.add(node) && children.size() > limit) {
                BaseNode removed = children.poll();
                nodesNames.remove(requireNonNull(removed).getText());
            }
            nodesNames.add(node.getText());
        });
    }

    @Nonnull
    private List<BaseNode> getChildren(ArtifactFilter artifactFilter) {
        List<BaseNode> children;
        String repoType = artifactFilter.getRepoType();
        if (repoType.equals("remote") || repoType.equals("virtual")) {
            children = handleRemoteOrVirtual(artifactFilter);
        } else {
            children = handleLocalOrFederated(artifactFilter);
        }
        return children;
    }

    @Nonnull
    private List<BaseNode> handleLocalOrFederated(ArtifactFilter artifactFilter) {
        RepoPath repositoryPath = InternalRepoPathFactory.create(artifactFilter.getRepoKey(), artifactFilter.getPath());
        List<ItemInfo> items = repositoryService.getChildren(repositoryPath);
        boolean canManage = authorizationService.canManage(repositoryPath);
        items = filterItemsIfNeeded(items, artifactFilter.getRepoPkgType(), canManage);
        return populateChildData(artifactFilter, items);
    }

    @Nonnull
    private List<BaseNode> handleRemoteOrVirtual(ArtifactFilter artifactFilter) {
        RepoPath virtualRepoPath =
                InternalRepoPathFactory.create(artifactFilter.getRepoKey(), artifactFilter.getPath(), true);
        BrowsableItemCriteria criteria = getBrowsableItemCriteria(virtualRepoPath);
        List<BaseBrowsableItem> currChildren = artifactFilter.getRepoType().equals("virtual") ?
                this.repositoryBrowsingService.getVirtualRepoBrowsableChildren(criteria) :
                this.repositoryBrowsingService.getRemoteRepoBrowsableChildren(criteria);
        currChildren = filterChecksums(currChildren);
        boolean canManage = authorizationService.canManage(virtualRepoPath);
        currChildren = filterInternalMetadata(currChildren, canManage);
        return populateRemoteData(artifactFilter, currChildren);
    }

    @Nonnull
    private BrowsableItemCriteria getBrowsableItemCriteria(RepoPath repositoryPath) {
        return new BrowsableItemCriteria.Builder(repositoryPath).includeChecksums(false).build();
    }

    /**
     * populate File or Folder Data
     *
     * @param artifactFilter The artifact filter (contains the relevant filed)
     * @param items          - nodes items ( File or Folder)
     */
    @Nonnull
    private List<BaseNode> populateChildData(ArtifactFilter artifactFilter, List<ItemInfo> items) {
        return items.stream().filter(
                        pathItem -> {
                            RepoPath repoPath = pathItem.getRepoPath();
                            return repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(repoPath);
                        })
                .map(pathItem -> getChildItem(pathItem, pathItem.getRelPath(), artifactFilter.getRepoType(),
                        artifactFilter.getRepoPkgType(), pathItem.getRepoPath()))
                .sorted(nodeComparator).toList();
    }

    /**
     * populate File or Folder Data
     *
     * @param artifactFilter The artifact filter (contains the relevant filed)
     * @param items          - nodes items ( File or Folder)
     */
    @Nonnull
    private List<BaseNode> populateRemoteData(ArtifactFilter artifactFilter, List<BaseBrowsableItem> items) {
        String repoType = artifactFilter.getRepoType();
        String repoPkgType = artifactFilter.getRepoPkgType();
        return items.stream()
                .map(pathItem -> getRemoteChildItem(pathItem, repoType, repoPkgType))
                .sorted(nodeComparator)
                .toList();
    }

    private void compactFolders(List<BaseNode> children) {
        children.stream()
                .filter(FolderNode.class::isInstance)
                .forEach(this::compactFolder);
    }

    /**
     * compact 1sr folder child  by looking for empty child folders
     *
     * @param child - 1st folder child
     */
    private void compactFolder(BaseNode child) {
        FolderNode folder = (FolderNode) child;
        StringBuilder nameBuilder = new StringBuilder(folder.getText());
        FolderNode next = folder.fetchNextChild();
        // look for empty folders
        while (next != null) {
            if (shouldNotBeCompacted(folder.getRepoPath())) {
                break;
            }
            folder.updateRepoPath(next.getRepoPath());
            folder.setCompacted(true);
            // update compact name
            nameBuilder.append('/').append(next.getText());
            next = next.fetchNextChild();
        }
        folder.setText(nameBuilder.toString());
    }

    private boolean shouldNotBeCompacted(RepoPath repoPath) {
        return nodeRepoTypeHelper.isConanReferenceFolder(repoPath);
    }

    private void setFolderIconsIfNeeded(List<BaseNode> nodes) {
        nodes.stream()
                .collect(Collectors.groupingBy(BaseNode::getRepoKey))
                .forEach(this::processNodesForRepo);
    }

    private void processNodesForRepo(String repoKey, List<BaseNode> nodes) {
        VirtualRepo virtualRepo = repositoryService.virtualRepositoryByKey(repoKey);
        if (virtualRepo != null) {
            processVirtualRepoNodes(virtualRepo, nodes);
        } else {
            LocalRepo<?, ?> localRepo = repositoryService.localFederatedOrCachedRepositoryByKey(repoKey);
            if (localRepo != null) {
                processLocalRepoNodes(localRepo, nodes);
            }
        }
    }

    private boolean isRelevantPathForIcon(RepoType packageType) {
        return packageType.isDockerGroup() || packageType.isConan() || packageType.isNix();
    }

    private void processLocalRepoNodes(LocalRepo<?, ?> localRepo, List<BaseNode> nodes) {
        if (!displayIconsOnLocalReposEnabled.getBoolean()) {
            return;
        }
        RepoType packageType = localRepo.getRepoConfig().getPackageType();
        if (!isRelevantPathForIcon(packageType)) {
            return;
        }
        nodes.forEach(node -> updateNodeIconIfNeeded(node, node.getRepoPath()));
    }

    private void processVirtualRepoNodes(VirtualRepo virtualRepo, List<BaseNode> nodes) {
        if (!displayIconsOnVirtualReposEnabled.getBoolean()) {
            return;
        }
        RepoType packageType = virtualRepo.getRepoConfig().getPackageType();
        if (!isRelevantPathForIcon(packageType)) {
            return;
        }
        nodes.forEach(node -> setIconOnVirtualNode(virtualRepo, node));
    }

    private void setIconOnVirtualNode(VirtualRepo virtualRepo, BaseNode node) {
        RepoPath repoPath = node.getRepoPath();
        ReposItemsThreadLocalCacheService.wrap(repoPath.getRepoKey(), () -> {
            Optional.ofNullable(virtualRepo.getVirtualRepoItem(repoPath))
                    .map(VirtualRepoItem::getItemInfo)
                    .map(ItemInfo::getRepoPath)
                    .ifPresent(realRepoPath -> updateNodeIconIfNeeded(node, realRepoPath));
            return null;
        });
    }

    private void updateNodeIconIfNeeded(BaseNode node, RepoPath repoPath) {
        Optional<String> dockerIconType = nodeRepoTypeHelper.getDockerManifestIconType(repoPath);
        if (dockerIconType.isPresent()) {
            node.setIcon(dockerIconType.get());
            return;
        }
        if (nodeRepoTypeHelper.isConanReferenceFolder(repoPath)) {
            node.setIcon(RepoType.Conan.getType());
        }
        if (nodeRepoTypeHelper.isNixReferenceFolder(repoPath)) {
            node.setIcon(RepoType.Nix.getType());
        }
    }
}
