/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.ui.rest.service.artifacts.browse.generic;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CoreAddons;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.BaseBrowsableItem;
import org.artifactory.api.repo.BrowsableItemCriteria;
import org.artifactory.api.repo.RepositoryBrowsingService;
import org.artifactory.api.repo.RootNodesFilterResult;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.md.Properties;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.NativeNodeFilter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.NativeArtifactsFetchBulkStrategy;
import org.artifactory.ui.utils.BrowseUtils;
import org.jfrog.commons.continues.ContinueResult;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Stream;

import static java.lang.String.valueOf;
import static java.util.Objects.requireNonNull;
import static org.artifactory.common.ConstantValues.treeBrowserStreamSizeLimit;
import static org.artifactory.util.PackageTypeConfigFields.FORCE_MAVEN_AUTHENTICATION;
import static org.jfrog.commons.continues.util.PagingUtils.getPagingFromMultipleFunctions;

/**
 * @author Chen Keinan
 */
@Slf4j
public abstract class BaseBrowseNativeService implements RestService<String> {

    private static final int NO_MORE_ITEMS = -1;
    private static final int REACHED_LIMIT = -2;
    public static final String REALM = "Artifactory Realm";
    public static final String ATTR_ARTIFACTORY_REPOSITORY_PATH = "artifactory.repository_path";
    public static final String ATTR_ARTIFACTORY_REQUEST_PROPERTIES = "artifactory.request_properties";
    protected static final DateTimeFormatter DATE_FORMAT = DateTimeFormat.forPattern("dd-MMM-yyyy HH:mm")
            .withLocale(Locale.ENGLISH);
    private final ItemInfoComparator itemInfoComparator = new ItemInfoComparator();
    public String head;
    protected List<? extends BaseBrowsableItem> items;
    protected int columnSize;
    protected boolean printParent;
    @Autowired
    protected InternalRepositoryService repositoryService;
    @Autowired
    protected RepositoryBrowsingService repoBrowsingService;
    @Autowired
    protected AuthorizationService authorizationService;
    @Autowired
    protected SecurityService securityService;
    @Autowired
    CentralConfigService centralConfigService;

    private static void handleLocalRepo(Stream<? extends BaseBrowsableItem> browsableItems,
            PriorityQueue<BaseBrowsableItem> nodesQueue, int maxArtifactsToFetch) {
        browsableItems.forEach(item -> {
            if (nodesQueue.add(item) && nodesQueue.size() > maxArtifactsToFetch + 1) {
                nodesQueue.poll();
            }
        });
    }

    private static void handleRemoteVirtualRepo(Stream<? extends BaseBrowsableItem> browsableItems,
            PriorityQueue<BaseBrowsableItem> nodesQueue, int maxArtifactsToFetch) {
        Set<String> nodesNames = new HashSet<>();
        browsableItems.forEach(node -> {
            if (nodesNames.contains(node.getName())) {
                return;
            }
            if (nodesQueue.add(node) && nodesQueue.size() > maxArtifactsToFetch + 1) {
                BaseBrowsableItem removed = nodesQueue.poll();
                nodesNames.remove(requireNonNull(removed).getName());
            }
            nodesNames.add(node.getName());
        });
    }

    protected ContinueResult<? extends BaseBrowsableItem> fetchItems(NativeNodeFilter nativeNodeFilter) {
        String repoType = getRepoType(nativeNodeFilter.getRepoPath());
        BrowsableItemCriteria.Builder builder = new BrowsableItemCriteria.Builder(
                nativeNodeFilter.getRepoPath()).
                requestProperties(nativeNodeFilter.getRequestProps());
        builder.includeChecksums(!ConstantValues.uiHideChecksums.getBoolean());
        BrowsableItemCriteria criteria = builder.build();
        Stream<? extends BaseBrowsableItem> browsableItems =
                getBrowsableItemsStream(repoType, criteria, nativeNodeFilter);
        int maxArtifactsToFetch =
                (int) (nativeNodeFilter.getContinueIndex() + Math.toIntExact(nativeNodeFilter.getLimit()));
        PriorityQueue<BaseBrowsableItem> nodesQueue =
                getBrowsableItemsPage(repoType, browsableItems, maxArtifactsToFetch);
        List<? extends BaseBrowsableItem> nodes = nodesQueue.stream().sorted(itemInfoComparator).toList();
        int continueState = calculateContinueState(maxArtifactsToFetch, nodes);
        return new ContinueResult<>(valueOf(continueState), nodes);
    }

    Stream<? extends BaseBrowsableItem> getBrowsableItemsStream(String repoType, BrowsableItemCriteria criteria,
            NativeNodeFilter nativeNodeFilter) {
        switch (repoType) {
            case "local" -> {
                return repoBrowsingService.getLocalRepoBrowsableChildrenStream(criteria,
                        nativeNodeFilter.isUpdateRootNodesFilterFlag(),
                        nativeNodeFilter.getRootNodesFilterResult());
            }
            case "remote" -> {
                return repoBrowsingService.getRemoteRepoBrowsableChildrenStream(criteria,
                        nativeNodeFilter.isUpdateRootNodesFilterFlag(),
                        nativeNodeFilter.getRootNodesFilterResult());
            }
            case "virtual" -> {
                return repoBrowsingService.getVirtualRepoBrowsableChildrenStream(criteria,
                        nativeNodeFilter.isUpdateRootNodesFilterFlag(),
                        nativeNodeFilter.getRootNodesFilterResult());
            }
            default -> throw new IllegalArgumentException("Unknown repository type for repo key: " + repoType);
        }
    }

    private PriorityQueue<BaseBrowsableItem> getBrowsableItemsPage(String repoType,
            Stream<? extends BaseBrowsableItem> browsableItems, int maxArtifactsToFetch) {
        PriorityQueue<BaseBrowsableItem> nodesQueue = new PriorityQueue<>(itemInfoComparator.reversed());
        if ("remote".equals(repoType) || "virtual".equals(repoType)) {
            handleRemoteVirtualRepo(browsableItems, nodesQueue, maxArtifactsToFetch);
        } else {
            handleLocalRepo(browsableItems, nodesQueue, maxArtifactsToFetch);
        }
        return nodesQueue;
    }

    protected String getRepoType(RepoPath repoPath) {
        String repoKey = repoPath.getRepoKey();
        if (repositoryService.remoteRepoDescriptorByKey(repoKey) != null) {
            return "remote";
        } else if (repositoryService.localFederatedCachedRepoDescriptorByKey(repoKey) != null) {
            return "local";
        } else if (repositoryService.virtualRepoDescriptorByKey(repoKey) != null) {
            return "virtual";
        }
        throw new IllegalArgumentException("Unknown repository type for repo key: " + repoKey);
    }

    /**
     * sort retrieved browsable item from repository
     *
     * @param repoPath              - repository path
     * @param requestProps          - request properties
     * @param rootNodesFilterResult - object hold the List node acceptance status flag,if flagged to false meaning
     *                              at least one node has read permission issue by user
     * @return list of sorted browsable item
     */
    protected List<? extends BaseBrowsableItem> getItems(RepoPath repoPath, Properties requestProps,
            RootNodesFilterResult rootNodesFilterResult) {
        boolean updateRootNodesFilterFlag = securityService.isAnonymous();
        List<? extends BaseBrowsableItem> items =
                getBrowsableItemsList(repoPath, requestProps, rootNodesFilterResult, updateRootNodesFilterFlag);
        items.sort(itemInfoComparator);
        return items;
    }

    protected ContinueResult<? extends BaseBrowsableItem> getItemsPage(NativeNodeFilter nativeNodeFilter,
            NativeArtifactsFetchBulkStrategy nativeArtifactsFetchBulkStrategy) {
        List<? extends BaseBrowsableItem> browsableItems =
                getBrowsableItemsList(nativeNodeFilter.getRepoPath(), nativeNodeFilter.getRequestProps(),
                        nativeNodeFilter.getRootNodesFilterResult(), nativeNodeFilter.isUpdateRootNodesFilterFlag());
        browsableItems.sort(itemInfoComparator);
        ContinueResult<? extends BaseBrowsableItem> result = getPagingFromMultipleFunctions(nativeNodeFilter,
                nativeArtifactsFetchBulkStrategy.getFetchFunction((List<BaseBrowsableItem>) browsableItems));
        if (result.getContinueState() == null || Long.parseLong(result.getContinueState()) == browsableItems.size()) {
            result.setContinueState("-1");
        }
        return result;
    }

    private int calculateContinueState(int maxArtifactsToFetch, List<? extends BaseBrowsableItem> result) {
        if (result.size() >= treeBrowserStreamSizeLimit.getInt()) {
            return REACHED_LIMIT;
        }
        if (result.size() < maxArtifactsToFetch + 1) {
            return  NO_MORE_ITEMS;
        } else {
            return maxArtifactsToFetch;
        }
    }

    /**
     * get Browsable Item from repositories (local/remote/virtual) based on search criteria
     *
     * @param repoPath                  - repository path
     * @param requestProps              - properties of the request
     * @param rootNodesFilterResult     - object hold the List node acceptance status flag,if flagged to false meaning
     *                                  at least one node has read permission issue by user
     * @param updateRootNodesFilterFlag - if true keep the acceptance status flag
     */
    private List<? extends BaseBrowsableItem> getBrowsableItemsList(RepoPath repoPath, Properties requestProps,
            RootNodesFilterResult rootNodesFilterResult, boolean updateRootNodesFilterFlag) {
        List<BaseBrowsableItem> items = Lists.newArrayList();
        BrowsableItemCriteria.Builder builder = new BrowsableItemCriteria.Builder(repoPath).
                requestProperties(requestProps);
        boolean includeChecksums = !ConstantValues.uiHideChecksums.getBoolean();
        builder.includeChecksums(includeChecksums);
        BrowsableItemCriteria criteria = builder.build();
        if (repositoryService.remoteRepoDescriptorByKey(repoPath.getRepoKey()) != null) {
            items = repoBrowsingService
                    .getRemoteRepoBrowsableChildren(criteria, updateRootNodesFilterFlag, rootNodesFilterResult);
        } else if (repositoryService.localFederatedCachedRepoDescriptorByKey(repoPath.getRepoKey()) != null) {
            items = repoBrowsingService
                    .getLocalRepoBrowsableChildren(criteria, updateRootNodesFilterFlag, rootNodesFilterResult);
        } else if (repositoryService.virtualRepoDescriptorByKey(repoPath.getRepoKey()) != null) {
            items = repoBrowsingService
                    .getVirtualRepoBrowsableChildren(criteria, updateRootNodesFilterFlag, rootNodesFilterResult);
        }
        if (items == null) {return Collections.emptyList();}

        if (!includeChecksums) {
            items = BrowseUtils.filterChecksums(items);
        }

        // Filter internal metadata is repo type specific
        boolean canManage = authorizationService.canManage(repoPath);
        items = BrowseUtils.filterInternalMetadata(items, canManage);

        return items;
    }

    /**
     * set response to http challenge message to anonymous if node list is empty due to missing permission
     *
     * @param repoPath              - repo path
     * @param rootNodesFilterResult - object hold empty list canRead flag
     * @param items                 - list of Items
     * @return true if a challenge message was set, false otherwise
     */
    protected boolean setResponseToHttpChallenge(RepoPath repoPath, RootNodesFilterResult rootNodesFilterResult,
            List<? extends BaseBrowsableItem> items, RestResponse<?> response, boolean isNativeModelRequest) {

        if (isItemListEmptyDueAnonymousMissingPermission(repoPath, rootNodesFilterResult, items)) {
            HttpServletResponse servletResponse = response.getServletResponse();
            if (!isNativeModelRequest) {
                servletResponse.addHeader("WWW-Authenticate", "Basic realm=\"" + REALM + "\"");
                servletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            }
            int statusCode = HttpStatus.SC_UNAUTHORIZED;
            response.responseCode(statusCode);
            response.error("Browse request for repo:path " + repoPath + " is forbidden " +
                    "for user 'anonymous'.");
            return true;
        }
        return false;
    }

    /**
     * @param rootNodesFilterResult - object hold List canRead flag
     * @param items                 list of items
     * @return true if list is empty due to anonymous missing permission to read, false otherwise
     */
    protected boolean isItemListEmptyDueAnonymousMissingPermission(RepoPath repoPath,
            RootNodesFilterResult rootNodesFilterResult,
            List<? extends BaseBrowsableItem> items) {
        return items.isEmpty() && (!rootNodesFilterResult.isAllItemNodesCanRead() || !hasPermissionOnPath(repoPath));
    }

    protected boolean hasPermissionOnPath(RepoPath repoPath) {
        if (repositoryService.virtualRepoDescriptorByKey(repoPath.getRepoKey()) != null) {
            VirtualRepo virtualRepo = repositoryService.virtualRepositoryByKey(repoPath.getRepoKey());
            if (virtualRepo != null) {
                return Stream.of(virtualRepo.getResolvedLocalAndFederatedRepos(), virtualRepo.getResolvedRemoteRepos())
                        .flatMap(Collection::stream)
                        .anyMatch(repo -> authorizationService
                                .canRead(RepoPathFactory.create(repo.getKey(), repoPath.getPath())));
            }
            return false;
        } else {
            return authorizationService.canRead(repoPath);
        }
    }

    /**
     * get addresses footer
     *
     * @return - address footer
     */
    protected String getAddressFooter() {
        StringBuilder addressBuilder = new StringBuilder();
        String versionInfo = getListBrowsingVersion();
        addressBuilder
                .append(String.format("%s Server", versionInfo));
        return addressBuilder.toString();
    }

    /**
     * get List browsing versions
     *
     * @return - list browsing versions
     */
    public String getListBrowsingVersion() {
        CoreAddons addon = ContextHelper.get().beanForType(AddonsManager.class).addonByType(CoreAddons.class);
        return addon.getListBrowsingVersion();
    }

    protected boolean checkForSpecificCases(RestResponse<?> response, RepoPath repoPath,
            ItemNotFoundRuntimeException e) {
        log.debug("", e);
        // If item not exists, but request is anonymous, and repo is virtual and force maven authentication
        // return 401
        RepoConfig repoConfig = repositoryService.repoConfigByKey(repoPath.getRepoKey());
        if (repoConfig instanceof VirtualRepoConfig virtualRepoConfig
                && Boolean.parseBoolean(
                virtualRepoConfig.getPackageTypeConfig().get(FORCE_MAVEN_AUTHENTICATION.getKey()))
                && securityService.isAnonymous()) {
            int statusCode = HttpStatus.SC_UNAUTHORIZED;
            response.responseCode(statusCode);
            response.error("Browse request for repo:path " + repoPath + " is forbidden " +
                    "for user 'anonymous'.");
            return true;
        }
        if (!isRemoteFileDownload(repoPath) && !isArchiveDownload(repoPath)) {
            throw e;
        }
        return false;
    }

    /**
     * Check if the repository of the requested path is a remote repository.
     * If the file wasn't found in the cache it means we need to download it.
     */
    boolean isRemoteFileDownload(RepoPath repoPath) {
        return repositoryService.isRemoteRepoExist(repoPath.getRepoKey());
    }

    boolean isArchiveDownload(RepoPath repoPath) {
        return repoPath.getPath().contains("!/");
    }

    /**
     * item comparator for sorting
     */
    private static class ItemInfoComparator implements Comparator<BaseBrowsableItem>, Serializable {
        @Override
        public int compare(BaseBrowsableItem o1, BaseBrowsableItem o2) {
            final int folderCmp = Boolean.compare(o2.isFolder(), o1.isFolder());
            if (folderCmp != 0) {
                return folderCmp;
            }
            return o1.getName().compareToIgnoreCase(o2.getName());
        }
    }
}

