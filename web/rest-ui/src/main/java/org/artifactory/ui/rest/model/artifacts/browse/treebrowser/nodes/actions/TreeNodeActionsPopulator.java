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

package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions;

import com.google.common.collect.Lists;
import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.addon.AddonType;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CloudEnvironment;
import org.artifactory.addon.watch.ArtifactWatchAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.download.FolderDownloadConfigDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.mime.NamingUtils;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.jfrog.client.util.PathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import static org.artifactory.descriptor.repo.RepoType.ReleaseBundles;
import static org.artifactory.repo.RepoDetailsType.FEDERATED_REPO;
import static org.artifactory.repo.RepoDetailsType.LOCAL_REPO;
import static org.artifactory.repo.RepoDetailsType.REMOTE_REPO;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL_REPO;
import static org.artifactory.repo.cache.expirable.InternalMetadataChecker.isInternalMetadata;
import static org.artifactory.rest.common.util.RestUtils.calcRepoPathFromRequestPath;

/**
 * Builds an actions list to be added to a tree node
 *
 * @author Shay Yaakov
 */
@Component
public class TreeNodeActionsPopulator {

    private static final String RECALCULATE_INDEX = "RecalculateIndex";
    private static final String RELEASE_BUNDLES_REPO = "releaseBundles";
    private static final String SUPPORT_BUNDLES_REPO = "supportBundles";
    private static final String TRASH = "trash";

    private RepositoryService repoService;
    private AuthorizationService authService;
    private SecurityService securityService;
    private CentralConfigService centralConfig;
    private BuildFacade buildFacade;
    private AddonsManager addonsManager;
    private PackageSpecification packageSpecification;

    @Autowired
    public void setRepoService(RepositoryService repoService) {
        this.repoService = repoService;
    }

    @Autowired
    public void setAuthService(AuthorizationService authService) {
        this.authService = authService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setCentralConfig(CentralConfigService centralConfig) {
        this.centralConfig = centralConfig;
    }

    @Autowired
    public void setBuildFacade(BuildFacade buildFacade) {
        this.buildFacade = buildFacade;
    }

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Autowired
    public void setPackageSpecification(PackageSpecification packageSpecification) {
        this.packageSpecification = packageSpecification;
    }

    public void populateForRepository(TabsAndActions tabsAndActions) {
        List<TabOrAction> actions = Lists.newArrayList();
        RepoPath repoPath = tabsAndActions.getRepoPath();
        String repoType = tabsAndActions.getRepoType();
        RepoType repoPkgType = tabsAndActions.getRepoPkgType();
        if (REMOTE_REPO.equals(repoType) || VIRTUAL_REPO.equals(repoType)) {
            actions.add(refresh());
            actions.add(virtualZapCaches(tabsAndActions.getRepoType()));
            actions.add(remoteVirtualReindex(tabsAndActions.getRepoPkgType(), tabsAndActions.getRepoType()));
            actions.add(favorites());
        } else if (RELEASE_BUNDLES_REPO.equals(repoType)) {
            actions.add(refresh());
            actions.add(watch(repoPath));
            actions.add(deleteContent(repoPath));
            actions.add(favorites());
        } else if (ReleaseBundles == repoPkgType) {
            actions.add(refresh());
            actions.add(watch(repoPath));
            actions.add(favorites());
        } else if (SUPPORT_BUNDLES_REPO.equals(repoType)) {
            actions.add(refresh());
            actions.add(downloadFolder(repoPath));
            actions.add(deleteContent(repoPath));
        } else if (TRASH.equals(repoType)) {
            if (authService.canAccessTrashcan()) {
                actions.add(refresh());
                actions.add(searchTrash());
                actions.add(emptyTrash());
            }
        } else {
            actions.add(downloadFolder(repoPath));
            actions.add(refresh());
            actions.add(copyContent(repoPath));
            actions.add(moveContent(repoPath));
            actions.add(watch(repoPath));
            actions.add(zap(repoPath));
            actions.add(reindex(repoPath, tabsAndActions.getRepoPkgType(), tabsAndActions.getRepoType()));
            actions.add(deleteVersions(repoPath));
            actions.add(deleteContent(repoPath));
            actions.add(favorites());
        }
        tabsAndActions.setActions(actions.stream().filter(Objects::nonNull).toList());
    }

    public void populateForFolder(TabsAndActions tabsAndActions) {
        List<TabOrAction> actions = Lists.newArrayList();
        RepoPath repoPath = tabsAndActions.getRepoPath();
        String repoType = tabsAndActions.getRepoType();
        RepoType repoPkgType = tabsAndActions.getRepoPkgType();
        if (REMOTE_REPO.equals(repoType) ||
                (VIRTUAL_REPO.equals(repoType) && !isInternalMetadata(repoPath.getPath()))) {
            actions.add(refresh());
        } else if (RELEASE_BUNDLES_REPO.equals(repoType) || ReleaseBundles == repoPkgType) {
            actions.add(refresh());
            actions.add(watch(repoPath));
            actions.add(downloadFolder(repoPath));
        } else if (SUPPORT_BUNDLES_REPO.equals(repoType)) {
            actions.add(refresh());
            actions.add(downloadFolder(repoPath));
            actions.add(delete(repoPath));
        } else if (TRASH.equals(repoType)) {
            if (authService.canRestoreFromTrashcan(repoPath)) {
                actions.add(refresh());
                actions.add(restore());
                actions.add(deletePermanently(repoPath));
            }
        } else if (isInternalMetadata(repoPath.getPath())) {
            if (LOCAL_REPO.equals(repoType) && authService.canManage(repoPath)) {
                actions.add(refresh());
                actions.add(delete(repoPath));
            } else if (VIRTUAL_REPO.equals(repoType) && authService.canManage(repoPath)) {
                actions.add(refresh());
            }
        } else {
            actions.add(downloadFolder(repoPath));
            actions.add(refresh());
            actions.add(copy(repoPath));
            actions.add(move(repoPath));
            actions.add(watch(repoPath));
            actions.add(zap(repoPath));
            actions.add(reindexPath(RepoPathFactory.create(repoPath.getRepoKey()), tabsAndActions.getRepoPkgType(),
                    tabsAndActions.getRepoType()));
            actions.add(deleteVersions(repoPath));
            actions.add(delete(repoPath));
        }

        tabsAndActions.setActions(actions.stream()
                .filter(Objects::nonNull)
                .toList());
    }

    public void populateForFile(TabsAndActions tabsAndActions) {
        List<TabOrAction> actions = Lists.newArrayList();
        RepoPath repoPath = tabsAndActions.getRepoPath();
        String repoType = tabsAndActions.getRepoType();
        RepoType repoPkgType = tabsAndActions.getRepoPkgType();
        boolean isCached = tabsAndActions.getCached();
        if (REMOTE_REPO.equals(repoType)) {
            if (userCanDownloadFromRemote(tabsAndActions)) {
                actions.add(download());
            }
        } else if (VIRTUAL_REPO.equals(repoType)) {
            if (!isCached) {
                if (!isInternalMetadata(repoPath.getPath())) {
                    actions.add(download());
                }
                tabsAndActions.setActions(actions.stream().filter(Objects::nonNull).toList());
                return;
            }

            repoPath = repoService.getVirtualFileInfo(repoPath).getRepoPath();
            RepoPath finalRepoPath = repoPath;
            if (repoService.getLocalFederatedAndCachedRepoConfigs().stream()
                    .anyMatch(e -> e.getKey().equals(finalRepoPath.getRepoKey())) &&
                    !isInternalMetadata(repoPath.getPath())) {
                actions.add(download());
            }
        } else if (RELEASE_BUNDLES_REPO.equals(repoType) || ReleaseBundles == repoPkgType) {
            actions.add(watch(repoPath));
            actions.add(download());
        } else if (SUPPORT_BUNDLES_REPO.equals(repoType)) {
            actions.add(download());
            actions.add(view(repoPath, repoPkgType));
            actions.add(delete(repoPath));
        } else if (TRASH.equals(repoType)) {
            if (authService.canRestoreFromTrashcan(repoPath)) {
                actions.add(restore());
                actions.add(deletePermanently(repoPath));
            }
        } else if (isInternalMetadata(repoPath.getPath())) {
            if (LOCAL_REPO.equals(repoType) && authService.canManage(repoPath)) {
                actions.add(delete(repoPath));
            }
        } else {
            actions.add(download());
            actions.add(view(repoPath, repoPkgType));
            actions.add(copy(repoPath));
            actions.add(move(repoPath));
            actions.add(watch(repoPath));
            actions.add(delete(repoPath));
        }
        tabsAndActions.setActions(actions.stream()
                .filter(Objects::nonNull)
                .toList());
    }

    TabOrAction downloadFolder(RepoPath repoPath) {
        FolderDownloadConfigDescriptor folderDownloadConfig = centralConfig.getDescriptor().getFolderDownloadConfig();
        if (authService.canRead(repoPath) && folderDownloadConfig.isEnabled() &&
                (!securityService.isAnonymous() || folderDownloadConfig.isEnabledForAnonymous())) {
            return new TabOrAction("DownloadFolder");
        }
        return null;
    }

    private TabOrAction refresh() {
        return new TabOrAction("Refresh");
    }

    private TabOrAction favorites() {
        return new TabOrAction("Favorites");
    }

    private TabOrAction searchTrash() {
        return new TabOrAction("SearchTrash");
    }

    private TabOrAction emptyTrash() {
        if (securityService.isAdmin()) { // only admin can empty the trashcan
            return new TabOrAction("EmptyTrash");
        }
        return null;
    }

    private TabOrAction copyContent(RepoPath repoPath) {
        if (authService.canRead(repoPath) && canDeployToLocalRepository()) {
            return new TabOrAction("CopyContent");
        }
        return null;
    }

    private TabOrAction moveContent(RepoPath repoPath) {
        if (authService.canDelete(repoPath) && canDeployToLocalRepository()) {
            return new TabOrAction("MoveContent");
        }
        return null;
    }

    private TabOrAction watch(RepoPath repoPath) {
        if (!addonsManager.isAddonSupported(AddonType.WATCH)) {
            return null;
        }
        ArtifactWatchAddon watchAddon = addonsManager.addonByType(ArtifactWatchAddon.class);
        return switch (watchAddon.getWatchStatus(repoPath)) {
            case WATCHING -> new TabOrAction("Unwatch");
            case NOT_WATCHING -> new TabOrAction("Watch");
            case NOT_ALLOWED -> null;
        };
    }

    private TabOrAction zap(RepoPath repoPath) {
        LocalRepoDescriptor localRepoDescriptor = localOrCachedRepoDescriptor(repoPath);
        if (repoService.canZap(repoPath) && localRepoDescriptor != null && localRepoDescriptor.isCache()) {
            return new TabOrAction("Zap");
        }
        return null;
    }

    private TabOrAction reindex(RepoPath repoPath, RepoType repoPkgType, String repoType) {
        final boolean hasReindex =
                packageSpecification.hasReindex(org.artifactory.common.repo.RepoType.LOCAL, repoPkgType);
        if (hasReindex && authService.canManage(repoPath) &&
                (LOCAL_REPO.equals(repoType) || FEDERATED_REPO.equals(repoType))) {
            return new TabOrAction(RECALCULATE_INDEX);
        }
        if (RepoType.Debian.equals(repoPkgType) && "cached".equals(repoType) && authService.canManage(repoPath)) {
            return new TabOrAction("CalculateDebianCoordinates");
        }
        return null;
    }

    private TabOrAction reindexPath(RepoPath repoPath, RepoType repoPkgType, String repoType) {
        if (packageSpecification.hasPathReindex(repoPkgType) && authService.canManage(repoPath) &&
                (LOCAL_REPO.equals(repoType) || FEDERATED_REPO.equals(repoType))) {
            return new TabOrAction(RECALCULATE_INDEX);
        }
        return null;
    }

    private TabOrAction deleteVersions(RepoPath repoPath) {
        LocalRepoDescriptor localRepoDescriptor = localOrCachedRepoDescriptor(repoPath);
        if ((authService.canDelete(repoPath)) && localRepoDescriptor != null && localRepoDescriptor.isLocal()) {
            return new TabOrAction("DeleteVersions");
        }
        return null;
    }

    private TabOrAction deleteContent(RepoPath repoPath) {
        if (authService.canDelete(repoPath)) {
            return new TabOrAction("DeleteContent");
        }
        return null;
    }

    private TabOrAction copy(RepoPath repoPath) {
        if (!NamingUtils.isSystem(repoPath.getPath())
                && !buildFacade.isBuildInfoRepo(repoPath.getRepoKey())
                && authService.canRead(repoPath)
                && canDeployToLocalRepository()) {
            return new TabOrAction("Copy");
        }
        return null;
    }

    private TabOrAction move(RepoPath repoPath) {
        if (!NamingUtils.isSystem(repoPath.getPath())
                && showMoveOnBuilds(repoPath)
                && authService.canDelete(repoPath)
                && canDeployToLocalRepository()) {
            return new TabOrAction("Move");
        }
        return null;
    }

    private boolean showMoveOnBuilds(RepoPath repoPath) {
        return !(repoPath.getRepoKey()
                .equals(buildFacade.getBuildInfoRepoForDefaultProject()) &&
                buildFacade.getAllBuildInfoRepos().size() == 1);
    }

    private TabOrAction delete(RepoPath repoPath) {
        if (authService.canDelete(repoPath)) {
            return new TabOrAction("Delete");
        }
        return null;
    }

    private TabOrAction deletePermanently(RepoPath repoPath) {
        if (securityService.isAdmin() || authService.canDelete(repoPath)) {
            return new TabOrAction("DeletePermanently");
        }
        return null;
    }

    private TabOrAction download() {
        return new TabOrAction("Download");
    }

    private TabOrAction view(RepoPath repoPath, RepoType repoPkgType) {
        CloudEnvironment cloudEnvironment = CloudEnvironment.bySystemProperty();
        if (!cloudEnvironment.isViewActionEnabled()) {
            return null;
        }
        String path = repoPath.getPath();
        if (NamingUtils.isViewable(path) || "class".equals(PathUtils.getExtension(path)) ||
                packageSpecification.isViewable(repoPath.getRepoKey(), path, repoPkgType)) {
            return new TabOrAction("View");
        }
        return null;
    }

    private TabOrAction virtualZapCaches(String repoType) {
        if (securityService.isAdmin() && VIRTUAL_REPO.equals(repoType)) {
            return new TabOrAction("ZapCaches");
        }
        return null;
    }

    private TabOrAction remoteVirtualReindex(RepoType repoPkgType, String repoType) {
        if (securityService.isAdmin()) {
            if (packageSpecification.hasReindex(org.artifactory.common.repo.RepoType.REMOTE, repoPkgType) &&
                    REMOTE_REPO.equals(repoType)) {
                return new TabOrAction(RECALCULATE_INDEX);
            } else {
                boolean hasReindex =
                        packageSpecification.hasReindex(org.artifactory.common.repo.RepoType.VIRTUAL, repoPkgType);
                if (hasReindex && VIRTUAL_REPO.equals(repoType)) {
                    return new TabOrAction(RECALCULATE_INDEX);
                }
            }
        }
        return null;
    }

    private TabOrAction restore() {
        return new TabOrAction("Restore");
    }

    private LocalRepoDescriptor localOrCachedRepoDescriptor(RepoPath repoPath) {
        return repoService.localFederatedOrCachedRepoDescriptorByKey(repoPath.getRepoKey());
    }

    private boolean userCanDownloadFromRemote(TabsAndActions tabsAndActions) {
        String repoKey = tabsAndActions.getRepoPath().getRepoKey();

        if (repoKey.endsWith(RepoPath.REMOTE_CACHE_SUFFIX)) {
            return true;
        }

        RemoteRepoDescriptor descriptor = repoService.remoteRepoDescriptorByKey(repoKey);
        if (descriptor == null) {
            return false;
        }

        if (!descriptor.isStoreArtifactsLocally()) {
            return true;
        }

        RepoPath remoteCacheRepoPath = calcRepoPathFromRequestPath(
                descriptor.getKey() + RepoPath.REMOTE_CACHE_SUFFIX + RepoPath.PATH_SEPARATOR +
                        tabsAndActions.getRepoPath().getPath());
        if (authService.canDeploy(remoteCacheRepoPath) || repoService.exists(tabsAndActions.getRepoPath())) {
            return true;
        }

        tabsAndActions
                .setMessage("This item is not cached. You do not have a permission to download non-cached items.");
        return false;
    }

    private boolean canDeployToLocalRepository() {
        return repoService.hasAnyDeployableRepo();
    }
}
