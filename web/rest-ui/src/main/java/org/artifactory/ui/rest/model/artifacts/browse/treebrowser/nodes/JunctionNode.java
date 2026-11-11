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

package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.BaseBrowsableItem;
import org.artifactory.api.repo.BrowsableItemCriteria;
import org.artifactory.api.repo.RepositoryBrowsingService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.mime.MimeType;
import org.artifactory.mime.NamingUtils;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.rest.common.util.JsonUtil;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.archive.ArchiveEntriesTree;
import org.artifactory.ui.utils.BrowseUtils;
import org.jfrog.commons.continues.ContinueResult;

import java.util.List;

import static org.artifactory.repo.RepoDetailsType.CACHED_REPO;
import static org.artifactory.repo.RepoDetailsType.DISTRIBUTION_REPO;
import static org.artifactory.repo.RepoDetailsType.FEDERATED_REPO;
import static org.artifactory.repo.RepoDetailsType.LOCAL_REPO;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE_REPO;
import static org.artifactory.repo.RepoDetailsType.SUPPORT_BUNDLES_REPO;
import static org.artifactory.ui.rest.model.utils.bundle.TreeBrowserBundleUtils.removeHiddenReleaseBundleNodes;
import static org.artifactory.ui.rest.model.utils.evidence.EvidenceUtils.removeEvidenceNodes;


/**
 * Junction node represents an action of which the user wishes to view an item children.
 * The user clicks the little arrow on the tree and the junction node request
 * gets populated with the children names (without their data to save network bandwidth)
 *
 * @author Chen  Keinan
 * @author Omri Ziv
 */
@JsonTypeName("junction")
@Slf4j
public class JunctionNode implements RestTreeNode {

    @JsonIgnore
    private final RepositoryBrowsingService repositoryBrowsingService;
    @JsonIgnore
    private final RepositoryService repoService;
    @JsonIgnore
    private final NodeRepoTypeHelper nodeRepoTypeHelper;
    @JsonIgnore
    private final AuthorizationService authorizationService;

    private String repoKey;
    private String path;
    private String repoType;
    private ItemInfo fileInfo = null;
    private RepoType repoPkgType = null;

    public JunctionNode() {
        ArtifactoryContext artifactoryContext = ContextHelper.get();
        AddonsManager addonsManager = artifactoryContext.beanForType(AddonsManager.class);
        repositoryBrowsingService = artifactoryContext.beanForType(RepositoryBrowsingService.class);
        repoService = artifactoryContext.beanForType(RepositoryService.class);
        authorizationService = artifactoryContext.beanForType(AuthorizationService.class);
        PropertiesService propertiesService = artifactoryContext.beanForType(PropertiesService.class);
        PackageSpecification packageSpecification = artifactoryContext.beanForType(PackageSpecification.class);
        this.nodeRepoTypeHelper = new NodeRepoTypeHelper(addonsManager, repoService, propertiesService,
                packageSpecification);
    }

    @Override
    public ContinueResult<? extends RestModel> fetchItemTypeData(boolean isCompact, boolean isArchiveRequest) {
        if (isArchiveRequest) {
            // get all archive children
            return new ArchiveEntriesTree().buildChildren(repoKey, path);
        } else {
            // get repository or folder children 1st depth
            getRepoPkgType();
            List<? extends RestModel> children = getChildren(isCompact);
            return new ContinueResult<>(null, children);
        }
    }

    @Override
    public boolean isArchiveExpendRequest() {
        return isLocalFederatedOrCached() && !isFolder() && isArchive();
    }

    private boolean isLocalFederatedOrCached() {
        return LOCAL_REPO.equals(repoType) || FEDERATED_REPO.equals(repoType) || CACHED_REPO.equals(repoType) ||
                DISTRIBUTION_REPO.equals(repoType) || RELEASE_BUNDLE_REPO.equals(repoType)
                || SUPPORT_BUNDLES_REPO.equals(repoType);
    }

    private void getRepoPkgType() {
        RepoDescriptor repoDescriptor = this.repoService.repoDescriptorByKey(this.repoKey);
        this.repoPkgType = repoDescriptor.getType();
        CurrentActivity.PACKAGE.put(this.repoPkgType);
    }

    private List<? extends RestModel> getChildren(boolean isCompact) {
        List<BaseNode> children;
        switch (repoType) {
            case "remote":
            case "virtual": {
                RepoPath virtualRepoPath = InternalRepoPathFactory.create(getRepoKey(), getPath(), true);
                BrowsableItemCriteria criteria = getBrowsableItemCriteria(virtualRepoPath);
                List<BaseBrowsableItem> currChildren = this.repoType.equals("virtual") ?
                        this.repositoryBrowsingService.getVirtualRepoBrowsableChildren(criteria) :
                        this.repositoryBrowsingService.getRemoteRepoBrowsableChildren(criteria);
                //RTFACT-9746
                currChildren = BrowseUtils.filterChecksums(currChildren);
                // Filter internal metadata is repo type specific
                boolean canManage = authorizationService.canManage(virtualRepoPath);
                currChildren = BrowseUtils.filterInternalMetadata(currChildren, canManage);
                children = Lists.newArrayListWithExpectedSize(currChildren.size());
                populateRemoteData(children, currChildren, this.repoType);
                break;
            }
            default: {
                RepoPath repositoryPath = InternalRepoPathFactory.create(getRepoKey(), getPath());
                List<ItemInfo> items = repoService.getChildren(repositoryPath);
                //RTFACT-9746
                boolean canManage = authorizationService.canManage(repositoryPath);
                items = BrowseUtils.filterItemsIfNeeded(items, repoPkgType.getType(), canManage);
                children = Lists.newArrayListWithExpectedSize(items.size());
                populateChildData(children, repoService, items, isCompact);
                break;
            }
        }
        children.sort(new NodeComparator());
        children = removeEvidenceNodes(children);
        children = removeHiddenReleaseBundleNodes(children);
        return children;
    }


    /**
     * get remote and virtual browsable item criteria
     *
     * @param repositoryPath - repository path
     * @return browsable item criteria
     */
    private BrowsableItemCriteria getBrowsableItemCriteria(RepoPath repositoryPath) {
        return new BrowsableItemCriteria.Builder(repositoryPath).includeChecksums(false).build();
    }

    public String getRepoKey() {
        return repoKey;
    }

    public void setRepoKey(String repoKey) {
        this.repoKey = repoKey;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    /**
     * populate File or Folder Data
     *
     * @param children    - list of child's
     * @param repoService - repository service
     * @param items       - nodes items ( File or Folder)
     */
    private void populateChildData(List<BaseNode> children, RepositoryService repoService, List<ItemInfo> items,
            boolean isCompact) {
        for (ItemInfo pathItem : items) {
            RepoPath repoPath = pathItem.getRepoPath();
            if (!repoService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(repoPath)) {
                continue;
            }
            children.add(getChildItem(pathItem, pathItem.getRelPath(), repoPath, isCompact));
        }
    }

    /**
     * populate File or Folder Data
     *
     * @param children - list of child's
     * @param items    - nodes items ( File or Folder)
     */
    private void populateRemoteData(List<BaseNode> children, List<BaseBrowsableItem> items, String repoType) {
        for (BaseBrowsableItem pathItem : items) {
            children.add(getRemoteChildItem(pathItem, repoType));
        }
    }

    /**
     * Returns a new child  node item
     *
     * @param pathItem     The path to the child content
     * @param relativePath The relative path to the child itself
     * @return File or folder node
     */
    private BaseNode getChildItem(ItemInfo pathItem, String relativePath, RepoPath repoPath, boolean isCompact) {
        BaseNode child;
        if (pathItem.isFolder()) {
            child = new FolderNode(((FolderInfo) pathItem), pathItem.getName(), repoType);
            if (isCompact) {
                // compact child folder
                compactFolder(child);
            }
        } else {
            MimeType mimeType = NamingUtils.getMimeType(relativePath);
            child = new FileNode(repoPath, pathItem.getName(), repoType, repoPkgType, mimeType.isArchive());
        }
        return child;
    }

    /**
     * Returns a new child  node item
     *
     * @param pathItem The path to the child content
     * @return File or folder node
     */
    protected BaseNode getRemoteChildItem(BaseBrowsableItem pathItem, String repoType) {
        BaseNode child = null;
        if (pathItem.isFolder()) {
            if (repoType.equals("virtual")) {
                child = this.createVirtualFolderNode(pathItem, repoType);
            } else if (repoType.equals("remote")) {
                child = this.createRemoteFolderNode(pathItem, repoType);
            }
        } else {
            MimeType mimeType = NamingUtils.getMimeType(pathItem.getRelativePath());
            child = new VirtualRemoteFileNode(pathItem, pathItem.getName(), repoType, repoPkgType,
                    mimeType.isArchive());
        }
        return child;
    }

    /**
     * create remote folder node
     *
     * @param pathItem - path item
     * @param repoType - repo type
     * @return - tree node
     */
    private BaseNode createRemoteFolderNode(BaseBrowsableItem pathItem, String repoType) {
        String repoKey = StringUtils.removeEnd(pathItem.getRepoKey(), "-cache");
        RepoPath repositoryPath = InternalRepoPathFactory.create(repoKey, pathItem.getRepoPath().getPath());
        return new VirtualRemoteFolderNode(repositoryPath, pathItem, pathItem.getName(), repoType);
    }

    /**
     * create virtual folder node
     *
     * @param pathItem - path item
     * @param repoType - repo type
     * @return - tree node
     */
    private BaseNode createVirtualFolderNode(BaseBrowsableItem pathItem, String repoType) {
        return new VirtualRemoteFolderNode(pathItem.getRepoPath(), pathItem, pathItem.getName(), repoType);
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

    private boolean isArchive() {
        return NamingUtils.getMimeType(getFileInfo().getRelPath()).isArchive();
    }

    private boolean isFolder() {
        return getFileInfo().isFolder();
    }

    private ItemInfo getFileInfo() {
        if (fileInfo == null) {
            RepoPath repositoryPath = InternalRepoPathFactory.create(getRepoKey(), getPath());
            fileInfo = repoService.getItemInfo(repositoryPath);
        }
        return fileInfo;
    }

    public String getRepoType() {
        return repoType;
    }

    public void setRepoType(String repoType) {
        this.repoType = repoType;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}