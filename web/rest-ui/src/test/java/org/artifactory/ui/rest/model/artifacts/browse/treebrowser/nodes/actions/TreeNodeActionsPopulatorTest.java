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

import com.jfrog.build.acl.bridge.BuildFacade;
import org.apache.commons.collections4.CollectionUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.watch.ArtifactWatchAddon;
import org.artifactory.addon.watch.WatchStatus;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.download.FolderDownloadConfigDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.artifactory.repo.RepoDetailsType.CACHED_REPO;
import static org.artifactory.repo.RepoDetailsType.FEDERATED_REPO;
import static org.artifactory.repo.RepoDetailsType.LOCAL_REPO;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE_REPO;
import static org.artifactory.repo.RepoDetailsType.SUPPORT_BUNDLES_REPO;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL_REPO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Rotem Kfir
 */
@Test
public class TreeNodeActionsPopulatorTest {

    private static final String TRASH_REPO_TYPE = "trash";

    @Mock
    private TreeNodeActionsPopulator populator;
    @Mock
    private RepositoryService repoService;
    @Mock
    private AuthorizationService authService;
    @Mock
    private SecurityService securityService;
    @Mock
    private CentralConfigService centralConfig;

    @Mock
    private BuildFacade buildFacade;

    @Mock
    private AddonsManager addonsManager;
    private PackageSpecification packageSpecification;

    AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        packageSpecification = Mockito.mock(PackageSpecification.class);
        when(packageSpecification.isViewable("repo", "path/PACKAGES", RepoType.CRAN)).thenReturn(true);
        populator = new TreeNodeActionsPopulator();
        populator.setAddonsManager(addonsManager);
        populator.setAuthService(authService);
        populator.setSecurityService(securityService);
        populator.setCentralConfig(centralConfig);
        populator.setBuildFacade(buildFacade);
        populator.setRepoService(repoService);
        populator.setPackageSpecification(packageSpecification);
    }

    @AfterMethod
    public void teardown() throws Exception {
        mocks.close();
    }

    @Test
    public void testSuccessfulDownloadFolderForAnonymous() {
        enableFolderDownload(true);
        makeTheUserAnonymousWithReadPermissions();
        TabOrAction action = populator.downloadFolder(null);
        assertNotNull(action);
    }

    @Test
    public void testDownloadFolderNotEnabledForAnonymous() {
        enableFolderDownload(false);
        makeTheUserAnonymousWithReadPermissions();
        TabOrAction action = populator.downloadFolder(null);
        assertNull(action);
    }

    @Test
    public void testAdminTrashcanActions() {
        when(securityService.isAdmin()).thenReturn(true);
        when(authService.canAccessTrashcan()).thenReturn(true);
        when(authService.canRestoreFromTrashcan(any())).thenReturn(true);

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath("some/repo/path");
        tabsAndActions.setRepoType(TRASH_REPO_TYPE);

        populator.populateForRepository(tabsAndActions);
        List<TabOrAction> actions = tabsAndActions.getActions();
        assertTrue(actions.stream().anyMatch(action -> "Refresh".equals(action.getName())));
        assertTrue(actions.stream().anyMatch(action -> "SearchTrash".equals(action.getName())));
        assertTrue(actions.stream().anyMatch(action -> "EmptyTrash".equals(action.getName())));

        populator.populateForFolder(tabsAndActions);
        actions = tabsAndActions.getActions();
        assertTrue(actions.stream().anyMatch(action -> "Refresh".equals(action.getName())));
        assertTrue(actions.stream().anyMatch(action -> "Restore".equals(action.getName())));
        assertTrue(actions.stream().anyMatch(action -> "DeletePermanently".equals(action.getName())));

        populator.populateForFile(tabsAndActions);
        actions = tabsAndActions.getActions();
        assertTrue(actions.stream().anyMatch(action -> "Restore".equals(action.getName())));
        assertTrue(actions.stream().anyMatch(action -> "DeletePermanently".equals(action.getName())));
    }

    @Test
    public void testNonAdminButCanDeletePermanently() {
        when(securityService.isAdmin()).thenReturn(false);
        when(authService.canDelete(any(RepoPath.class))).thenReturn(true);
        when(authService.canAccessTrashcan()).thenReturn(true);
        when(authService.canRestoreFromTrashcan(any())).thenReturn(true);

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath("some/repo/path");
        tabsAndActions.setRepoType(TRASH_REPO_TYPE);

        populator.populateForFolder(tabsAndActions);
        var actions = tabsAndActions.getActions();
        assertTrue(actions.stream().anyMatch(action -> "Refresh".equals(action.getName())));
        assertTrue(actions.stream().anyMatch(action -> "Restore".equals(action.getName())));
        assertTrue(actions.stream().anyMatch(action -> "DeletePermanently".equals(action.getName())));

        populator.populateForFile(tabsAndActions);
        actions = tabsAndActions.getActions();
        assertTrue(actions.stream().anyMatch(action -> "Restore".equals(action.getName())));
        assertTrue(actions.stream().anyMatch(action -> "DeletePermanently".equals(action.getName())));
    }

    @Test
    public void testNonAdminButCanAccessTrashcanActions() {
        when(securityService.isAdmin()).thenReturn(false);
        when(authService.canAccessTrashcan()).thenReturn(true);
        when(authService.canRestoreFromTrashcan(any())).thenReturn(true);

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath("some/repo/path");
        tabsAndActions.setRepoType(TRASH_REPO_TYPE);

        populator.populateForRepository(tabsAndActions);
        List<TabOrAction> actions = tabsAndActions.getActions();
        assertTrue(actions.stream().anyMatch(action -> "Refresh".equals(action.getName())));
        assertTrue(actions.stream().anyMatch(action -> "SearchTrash".equals(action.getName())));
        assertFalse(actions.stream().anyMatch(action -> "EmptyTrash".equals(action.getName()))); // false

        populator.populateForFolder(tabsAndActions);
        actions = tabsAndActions.getActions();
        assertTrue(actions.stream().anyMatch(action -> "Refresh".equals(action.getName())));
        assertTrue(actions.stream().anyMatch(action -> "Restore".equals(action.getName())));
        assertFalse(actions.stream().anyMatch(action -> "DeletePermanently".equals(action.getName()))); // false

        populator.populateForFile(tabsAndActions);
        actions = tabsAndActions.getActions();
        assertTrue(actions.stream().anyMatch(action -> "Restore".equals(action.getName())));
        assertFalse(actions.stream().anyMatch(action -> "DeletePermanently".equals(action.getName()))); // false
    }

    @Test
    public void testNonAdminCanAccessTrashcanNoRestorePermissions() {
        when(authService.canAccessTrashcan()).thenReturn(true);
        when(authService.canRestoreFromTrashcan(any())).thenReturn(false);

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath("some/repo/path");
        tabsAndActions.setRepoType(TRASH_REPO_TYPE);

        populator.populateForRepository(tabsAndActions);
        List<TabOrAction> actions = tabsAndActions.getActions();
        assertTrue(actions.stream().anyMatch(action -> "Refresh".equals(action.getName())));
        assertTrue(actions.stream().anyMatch(action -> "SearchTrash".equals(action.getName())));
        assertFalse(actions.stream().anyMatch(action -> "EmptyTrash".equals(action.getName()))); // false

        populator.populateForFolder(tabsAndActions);
        actions = tabsAndActions.getActions();
        assertTrue(actions.isEmpty());

        populator.populateForFile(tabsAndActions);
        actions = tabsAndActions.getActions();
        assertTrue(actions.isEmpty());
    }

    @Test
    public void testNonAdminNoAccessToTrashcanNoRestorePermissions() {
        when(authService.canAccessTrashcan()).thenReturn(false);

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath("some/repo/path");
        tabsAndActions.setRepoType(TRASH_REPO_TYPE);

        populator.populateForRepository(tabsAndActions);
        List<TabOrAction> actions = tabsAndActions.getActions();
        assertTrue(actions.isEmpty());

        populator.populateForFolder(tabsAndActions);
        actions = tabsAndActions.getActions();
        assertTrue(actions.isEmpty());

        populator.populateForFile(tabsAndActions);
        actions = tabsAndActions.getActions();
        assertTrue(actions.isEmpty());
    }

    @Test(dataProvider = "getOptions")
    public void testUserCanDownloadFromRemote(boolean userCanDeploy, boolean fileExistInCache,
            boolean remoteCacheEnabled, boolean downloadShouldBeAvailable) {
        setupRepoService(userCanDeploy, fileExistInCache, remoteCacheEnabled);

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType("remote");
        tabsAndActions.setRepoPath("test-remote/item");

        populator.populateForFile(tabsAndActions);

        if (downloadShouldBeAvailable) {
            assertEquals(tabsAndActions.getActions().size(), 1);
            assertEquals(tabsAndActions.getActions().get(0).getName(), "Download");
        } else {
            assertEquals(tabsAndActions.getActions().size(), 0);
        }
    }

    @Test
    public void whenAlibabaEnvironment() {
        try {
            System.setProperty("shared.system.cloudEnv", "alibaba");

            List<TabOrAction> actions = populateActions();

            assertFalse(actions.stream().anyMatch(action -> "View".equals(action.getName())),
                    "view action is not in list");
        } finally {
            System.clearProperty("shared.system.cloudEnv");
        }
    }

    @Test
    public void whenNotAlibabaEnvironment() {
        List<TabOrAction> actions = populateActions();

        assertTrue(actions.stream().anyMatch(action -> "View".equals(action.getName())), "view action is in list");
    }

    @Test(dataProvider = "localAndFederatedPackagesReindex")
    public void testLocalAndFederatedPackagesReindex(PackageType packageType, String repoType,
            boolean shouldHaveReindex, boolean canManage) {
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(repoType);
        tabsAndActions.setRepoPath(String.format("%s-%s/item", packageType.name().toLowerCase(), repoType));
        tabsAndActions.setRepoPkgType(RepoType.fromType(packageType.name()));
        enableFolderDownload(false);
        makeTheUserAnonymousWithReadPermissions();
        when(packageSpecification
                .hasReindex(org.artifactory.common.repo.RepoType.LOCAL, tabsAndActions.getRepoPkgType()))
                .thenReturn(shouldHaveReindex);
        when(authService.canManage(tabsAndActions.getRepoPath())).thenReturn(canManage);
        populator.populateForRepository(tabsAndActions);
        List<TabOrAction> actions = tabsAndActions.getActions();
        if (shouldHaveReindex && canManage) {
            assertTrue(actions.stream().anyMatch(action -> "RecalculateIndex".equals(action.getName())));
        } else {
            assertFalse(actions.stream().anyMatch(action -> "RecalculateIndex".equals(action.getName())));
        }
    }

    @Test(dataProvider = "userCanDeleteFileInternalMetadataOnLocalRepo")
    public void userCanDeleteInternalMetadataFilesOnLocalRepo(boolean userCanManage, String repoPath,
            List<String> expectedActions) {
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(LOCAL_REPO);
        tabsAndActions.setRepoPath(repoPath);
        when(authService.canManage(any(RepoPath.class))).thenReturn(userCanManage);
        when(authService.canDelete(any())).thenReturn(true);
        when(authService.canRead(any())).thenReturn(true);
        when(authService.canDeploy(any())).thenReturn(true);
        populator.populateForFile(tabsAndActions);
        List<String> actions = tabsAndActions.getActions().stream().map(TabOrAction::getName).toList();
        assertTrue(CollectionUtils.isEqualCollection(actions, expectedActions));
    }

    @Test
    public void userCanDownloadAndWatchFilesOnReleaseBundlesRepo() {
        ArtifactWatchAddon watchAddon = Mockito.mock(ArtifactWatchAddon.class);
        when(addonsManager.addonByType(any())).thenReturn(watchAddon);
        when(addonsManager.isAddonSupported(any())).thenReturn(true);
        when(watchAddon.getWatchStatus(any())).thenReturn(WatchStatus.NOT_WATCHING);
        when(authService.canRead(any())).thenReturn(true);
        String repoPath = "release/file";
        List<String> expectedActions = List.of("Download", "Watch");
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(RELEASE_BUNDLE_REPO);
        tabsAndActions.setRepoPath(repoPath);
        populator.populateForFile(tabsAndActions);
        List<String> actions = tabsAndActions.getActions().stream().map(TabOrAction::getName).toList();
        assertTrue(CollectionUtils.isEqualCollection(actions, expectedActions));
    }

    @Test
    public void watchActionAbsentWhenAddonNotSupported() {
        ArtifactWatchAddon watchAddon = Mockito.mock(ArtifactWatchAddon.class);
        when(addonsManager.addonByType(any())).thenReturn(watchAddon);
        when(addonsManager.isAddonSupported(any())).thenReturn(false);
        when(watchAddon.getWatchStatus(any())).thenReturn(WatchStatus.NOT_WATCHING);
        when(authService.canRead(any())).thenReturn(true);
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(RELEASE_BUNDLE_REPO);
        tabsAndActions.setRepoPath("release/file");
        populator.populateForFile(tabsAndActions);
        List<String> actions = tabsAndActions.getActions().stream().map(TabOrAction::getName).toList();
        assertFalse(actions.contains("Watch"));
        assertFalse(actions.contains("Unwatch"));
    }

    @Test
    public void watchActionAbsentWhenUserNotAllowed() {
        ArtifactWatchAddon watchAddon = Mockito.mock(ArtifactWatchAddon.class);
        when(addonsManager.addonByType(any())).thenReturn(watchAddon);
        when(addonsManager.isAddonSupported(any())).thenReturn(true);
        when(watchAddon.getWatchStatus(any())).thenReturn(WatchStatus.NOT_ALLOWED);
        when(authService.canRead(any())).thenReturn(true);
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(RELEASE_BUNDLE_REPO);
        tabsAndActions.setRepoPath("release/file");
        populator.populateForFile(tabsAndActions);
        List<String> actions = tabsAndActions.getActions().stream().map(TabOrAction::getName).toList();
        assertFalse(actions.contains("Watch"));
        assertFalse(actions.contains("Unwatch"));
    }

    @Test
    public void unwatchActionPresentWhenAlreadyWatching() {
        ArtifactWatchAddon watchAddon = Mockito.mock(ArtifactWatchAddon.class);
        when(addonsManager.addonByType(any())).thenReturn(watchAddon);
        when(addonsManager.isAddonSupported(any())).thenReturn(true);
        when(watchAddon.getWatchStatus(any())).thenReturn(WatchStatus.WATCHING);
        when(authService.canRead(any())).thenReturn(true);
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(RELEASE_BUNDLE_REPO);
        tabsAndActions.setRepoPath("release/file");
        populator.populateForFile(tabsAndActions);
        List<String> actions = tabsAndActions.getActions().stream().map(TabOrAction::getName).toList();
        assertTrue(actions.contains("Unwatch"));
        assertFalse(actions.contains("Watch"));
    }

    @Test
    public void userCanDownloadFilesOnSupportBundlesRepo() {
        String repoPath = "support/file";
        List<String> expectedActions = List.of("Download");
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(SUPPORT_BUNDLES_REPO);
        tabsAndActions.setRepoPath(repoPath);
        populator.populateForFile(tabsAndActions);
        List<String> actions = tabsAndActions.getActions().stream().map(TabOrAction::getName).toList();
        assertTrue(CollectionUtils.isEqualCollection(actions, expectedActions));
    }

    @Test
    public void userCanDownloadFilesOnReleaseBundlesPackageType() {
        String repoPath = "release/file";
        List<String> expectedActions = List.of("Download");
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType("");
        tabsAndActions.setRepoPath(repoPath);
        tabsAndActions.setRepoPkgType(RepoType.ReleaseBundles);
        populator.populateForFile(tabsAndActions);
        List<String> actions = tabsAndActions.getActions().stream().map(TabOrAction::getName).toList();
        assertTrue(CollectionUtils.isEqualCollection(actions, expectedActions));
    }

    @Test(dataProvider = "userCanDeleteFolderInternalMetadataOnLocalRepo")
    public void userCanDeleteInternalMetadataFolderOnLocalRepo(boolean userCanManage, String repoPath,
            List<String> expectedActions) {
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(LOCAL_REPO);
        tabsAndActions.setRepoPath(repoPath);
        when(authService.canManage(any(RepoPath.class))).thenReturn(userCanManage);
        when(authService.canDelete(any())).thenReturn(true);
        when(authService.canRead(any())).thenReturn(true);
        when(authService.canDeploy(any())).thenReturn(true);
        CentralConfigService centralConfig = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor cent = Mockito.mock(CentralConfigDescriptor.class);
        FolderDownloadConfigDescriptor fd = Mockito.mock(FolderDownloadConfigDescriptor.class);
        when(cent.getFolderDownloadConfig()).thenReturn(fd);
        when(centralConfig.getDescriptor()).thenReturn(cent);
        FolderDownloadConfigDescriptor descriptor = Mockito.mock(FolderDownloadConfigDescriptor.class);
        when(descriptor.isEnabled()).thenReturn(true);
        when(centralConfig.getDescriptor().getFolderDownloadConfig()).thenReturn(descriptor);
        populator.setCentralConfig(centralConfig);
        populator.populateForFolder(tabsAndActions);

        List<String> actions = tabsAndActions.getActions().stream().map(TabOrAction::getName).toList();
        assertTrue(CollectionUtils.isEqualCollection(actions, expectedActions));
    }

    @Test(dataProvider = "userCanDownloadFileInternalMetadataOnVirtualRepo")
    public void userCanDownloadInternalMetadataFileOnVirtualRepo(boolean userCanManage, boolean isCached,
            String path,
            List<String> expectedActions) {
        String repoKey = "virtual-repo";
        FileInfo fileInfo = Mockito.mock(FileInfoImpl.class);
        when(fileInfo.getRepoPath()).thenReturn(new RepoPathImpl(repoKey, path));
        when(repoService.getVirtualFileInfo(any())).thenReturn(fileInfo);
        List<LocalConfig> localConfigList = new ArrayList<>();
        LocalConfig localConfig = Mockito.mock(LocalRepoConfig.class);
        when(localConfig.getKey()).thenReturn(repoKey);
        localConfigList.add(localConfig);
        when(repoService.getLocalFederatedAndCachedRepoConfigs()).thenReturn(localConfigList);

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(VIRTUAL_REPO);
        tabsAndActions.setRepoPath(repoKey + "/" + path);
        tabsAndActions.setCached(isCached);
        when(authService.canManage(any(RepoPath.class))).thenReturn(userCanManage);
        when(authService.canDelete(any())).thenReturn(true);
        when(authService.canRead(any())).thenReturn(true);
        when(authService.canDeploy(any())).thenReturn(true);
        CentralConfigService centralConfig = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor cent = Mockito.mock(CentralConfigDescriptor.class);
        FolderDownloadConfigDescriptor fd = Mockito.mock(FolderDownloadConfigDescriptor.class);
        when(cent.getFolderDownloadConfig()).thenReturn(fd);
        when(centralConfig.getDescriptor()).thenReturn(cent);
        FolderDownloadConfigDescriptor descriptor = Mockito.mock(FolderDownloadConfigDescriptor.class);
        when(descriptor.isEnabled()).thenReturn(true);
        when(centralConfig.getDescriptor().getFolderDownloadConfig()).thenReturn(descriptor);
        populator.setCentralConfig(centralConfig);
        populator.populateForFile(tabsAndActions);

        List<String> actions = tabsAndActions.getActions().stream().map(TabOrAction::getName).toList();
        assertTrue(CollectionUtils.isEqualCollection(actions, expectedActions));
    }

    @Test(dataProvider = "userCanDeleteFolderInternalMetadataOnVirtualRepo")
    public void userCanDeleteInternalMetadataFolderOnVirtualRepo(boolean userCanManage, String repoPath,
            List<String> expectedActions) {
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(VIRTUAL_REPO);
        tabsAndActions.setRepoPath(repoPath);
        when(authService.canManage(any(RepoPath.class))).thenReturn(userCanManage);
        when(authService.canDelete(any())).thenReturn(true);
        when(authService.canRead(any())).thenReturn(true);
        when(authService.canDeploy(any())).thenReturn(true);
        CentralConfigService centralConfig = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor cent = Mockito.mock(CentralConfigDescriptor.class);
        FolderDownloadConfigDescriptor fd = Mockito.mock(FolderDownloadConfigDescriptor.class);
        when(cent.getFolderDownloadConfig()).thenReturn(fd);
        when(centralConfig.getDescriptor()).thenReturn(cent);
        FolderDownloadConfigDescriptor descriptor = Mockito.mock(FolderDownloadConfigDescriptor.class);
        when(descriptor.isEnabled()).thenReturn(true);
        when(centralConfig.getDescriptor().getFolderDownloadConfig()).thenReturn(descriptor);
        populator.setCentralConfig(centralConfig);
        populator.populateForFolder(tabsAndActions);

        List<String> actions = tabsAndActions.getActions().stream().map(TabOrAction::getName).toList();
        assertTrue(CollectionUtils.isEqualCollection(actions, expectedActions));
    }


    @Test(dataProvider = "deleteAndZapPermissionOnCachedRepo")
    public void userWithDeleteAndZapPermissionOnCachedRepo(boolean canDelete, boolean canZap,
            List<String> expectedActions) {
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(CACHED_REPO);
        tabsAndActions.setRepoPath("repo-remote-cache");
        when(authService.canDelete(any())).thenReturn(canDelete);
        when(repoService.canZap(any(RepoPath.class))).thenReturn(canZap);
        CentralConfigService centralConfig = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor cent = Mockito.mock(CentralConfigDescriptor.class);
        LocalRepoDescriptor localRepoDescriptor = Mockito.mock(LocalRepoDescriptor.class);
        FolderDownloadConfigDescriptor folderDownloadConfig = Mockito.mock(FolderDownloadConfigDescriptor.class);
        when(cent.getFolderDownloadConfig()).thenReturn(folderDownloadConfig);
        when(repoService.localFederatedOrCachedRepoDescriptorByKey(anyString())).thenReturn(localRepoDescriptor);
        when(centralConfig.getDescriptor()).thenReturn(cent);
        when(localRepoDescriptor.isCache()).thenReturn(true);
        when(localRepoDescriptor.isLocal()).thenReturn(true);
        when(authService.canRead(any())).thenReturn(true);
        populator.setCentralConfig(centralConfig);

        populator.populateForRepository(tabsAndActions);

        List<String> actions = tabsAndActions.getActions().stream().map(TabOrAction::getName).toList();
        assertTrue(CollectionUtils.isEqualCollection(actions, expectedActions));
    }

    @Test(dataProvider = "deleteAndZapPermissionOnCachedFolder")
    public void userWithDeleteAndZapPermissionOnCachedFolder(boolean canDelete, boolean canZap,
            List<String> expectedActions) {
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(CACHED_REPO);
        tabsAndActions.setRepoPath("repo-remote-cache");
        when(authService.canDelete(any())).thenReturn(canDelete);
        when(repoService.canZap(any(RepoPath.class))).thenReturn(canZap);
        CentralConfigService centralConfig = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor cent = Mockito.mock(CentralConfigDescriptor.class);
        LocalRepoDescriptor localRepoDescriptor = Mockito.mock(LocalRepoDescriptor.class);
        FolderDownloadConfigDescriptor folderDownloadConfig = Mockito.mock(FolderDownloadConfigDescriptor.class);
        when(cent.getFolderDownloadConfig()).thenReturn(folderDownloadConfig);
        when(repoService.localFederatedOrCachedRepoDescriptorByKey(anyString())).thenReturn(localRepoDescriptor);
        when(centralConfig.getDescriptor()).thenReturn(cent);
        when(localRepoDescriptor.isCache()).thenReturn(true);
        when(localRepoDescriptor.isLocal()).thenReturn(true);
        when(authService.canRead(any())).thenReturn(true);
        populator.setCentralConfig(centralConfig);

        populator.populateForFolder(tabsAndActions);

        List<String> actions = tabsAndActions.getActions().stream().map(TabOrAction::getName).toList();
        assertTrue(CollectionUtils.isEqualCollection(actions, expectedActions));
    }

    @Test
    public void testReleaseBundleV2populateForRepository() {
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(LOCAL_REPO);
        tabsAndActions.setRepoPath(String.format("%s-%s/item", LOCAL_REPO, LOCAL_REPO));
        tabsAndActions.setRepoPkgType(RepoType.ReleaseBundles);
        populator.populateForRepository(tabsAndActions);
        List<TabOrAction> actions = tabsAndActions.getActions();
        assertTrue(actions.stream().anyMatch(action -> "Refresh".equals(action.getName())));
        assertFalse(actions.stream().anyMatch(action -> "Watch".equals(action.getName())));
        assertTrue(actions.stream().anyMatch(action -> "Favorites".equals(action.getName())));
    }

    @Test
    public void testSetRepoPathForFolder() {
        String repoPath = "docker/welcome-to-docker-folder";
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(LOCAL_REPO);
        tabsAndActions.setType("folder");
        tabsAndActions.setRepoPath(repoPath);
        assertFalse(tabsAndActions.getRepoPath().isFile());
    }

    @Test
    public void testSetRepoPathForNonFolder() {
        String repoPath = "docker/welcome-to-docker-folder/manifest.json";
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(LOCAL_REPO);
        tabsAndActions.setType("file");
        tabsAndActions.setRepoPath(repoPath);
        assertTrue(tabsAndActions.getRepoPath().isFile());
    }

    private List<TabOrAction> populateActions() {
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath("repo/path/PACKAGES");
        tabsAndActions.setRepoPkgType(RepoType.CRAN);
        populator.populateForFile(tabsAndActions);
        return tabsAndActions.getActions();
    }

    private void enableFolderDownload(boolean forAnonymous) {
        FolderDownloadConfigDescriptor folderDescriptor = new FolderDownloadConfigDescriptor();
        folderDescriptor.setEnabled(true);
        folderDescriptor.setEnabledForAnonymous(forAnonymous);
        CentralConfigDescriptor descriptor = Mockito.mock(CentralConfigDescriptor.class);
        when(descriptor.getFolderDownloadConfig()).thenReturn(folderDescriptor);
        when(centralConfig.getDescriptor()).thenReturn(descriptor);
    }

    private void makeTheUserAnonymousWithReadPermissions() {
        when(authService.canRead(any())).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(true);
    }

    private void setupRepoService(boolean userCanDeploy, boolean fileExistInCache, boolean remoteCacheEnabled) {
        RepoPath remotePath = RepoPathFactory.create("test-remote/item");
        RemoteRepoDescriptor remote = new RemoteRepoDescriptor() {
        };
        remote.setKey("test-remote");
        remote.setStoreArtifactsLocally(remoteCacheEnabled);
        when(repoService.remoteRepoDescriptorByKey("test-remote")).thenReturn(remote);
        when(repoService.exists(remotePath)).thenReturn(fileExistInCache);
        when(authService.canDeploy(RepoPathFactory.create("test-remote-cache/item"))).thenReturn(userCanDeploy);
    }

    @DataProvider
    public static Object[][] getOptions() {
        return new Object[][] {
                //userCanDeploy, fileExistInCache, remoteCacheEnabled, downloadShouldBeAvailable
                {false, false, false, true},
                {false, false, true, false},
                {false, true, false, true},
                {false, true, true, true},
                {true, false, false, true},
                {true, false, true, true},
                {true, true, true, true}
        };
    }

    @DataProvider
    public static Object[][] localAndFederatedPackagesReindex() {
        return new Object[][] {
                //packageType, repoType, shouldHaveReindex, canManage
                {PackageType.SWIFT, LOCAL_REPO, true, true},
                {PackageType.SWIFT, FEDERATED_REPO, true, true},
                {PackageType.SWIFT, LOCAL_REPO, true, false},
                {PackageType.SWIFT, FEDERATED_REPO, true, false},
                {PackageType.DOCKER, LOCAL_REPO, false, true},
                {PackageType.DOCKER, FEDERATED_REPO, false, true},
                {PackageType.DOCKER, LOCAL_REPO, false, false},
                {PackageType.DOCKER, FEDERATED_REPO, false, false},
        };
    }

    @DataProvider
    public static Object[][] userCanDeleteFileInternalMetadataOnLocalRepo() {
        return new Object[][] {
                //canManage, repoType, available actions
                {true, "local-repo/.jfrog/item", List.of("Delete")},
                {false, "local-repo/.jfrog/item", List.of()},
                {true, "local-repo/item", List.of("Delete", "Download")},
                {false, "local-repo/item", List.of("Delete", "Download")}
        };
    }

    @DataProvider
    public static Object[][] userCanDeleteFolderInternalMetadataOnLocalRepo() {
        return new Object[][] {
                //canManage, repoType, available actions
                {true, "local-repo/.jfrog/item", List.of("Delete", "Refresh")},
                {false, "local-repo/.jfrog/item", List.of()},
                {true, "local-repo/item", List.of("Delete", "DownloadFolder", "Refresh")},
                {false, "local-repo/item", List.of("Delete", "DownloadFolder", "Refresh")}
        };
    }

    @DataProvider
    public static Object[][] userCanDownloadFileInternalMetadataOnVirtualRepo() {
        return new Object[][] {
                //canManage, isCached, path, available actions
                {true, true, ".jfrog/item", List.of()},
                {true, false, ".jfrog/item", List.of()},
                {false, true, ".jfrog/item", List.of()},
                {false, false, ".jfrog/item", List.of()},
                {true, true, "item", List.of("Download")},
                {true, false, "item", List.of("Download")},
                {false, true, "item", List.of("Download")},
                {false, false, "item", List.of("Download")}
        };
    }

    @DataProvider
    public static Object[][] userCanDeleteFolderInternalMetadataOnVirtualRepo() {
        return new Object[][] {
                //canManage, repoType, available actions
                {true, "virtual-repo/.jfrog/item", List.of("Refresh")},
                {false, "virtual-repo/.jfrog/item", List.of()},
                {true, "virtual-repo/item", List.of("Refresh")},
                {false, "virtual-repo/item", List.of("Refresh")}
        };
    }

    @DataProvider
    public static Object[][] deleteAndZapPermissionOnCachedRepo() {
        return new Object[][] {
                //canDelete, canManage, available actions
                {true, true, List.of("Refresh", "Zap", "DeleteVersions", "DeleteContent", "Favorites")},
                {true, false, List.of("Refresh", "DeleteVersions", "DeleteContent", "Favorites")},
                {false, true, List.of("Refresh", "Zap", "Favorites")},
                {false, false, List.of("Refresh", "Favorites")}
        };
    }

    @DataProvider
    public static Object[][] deleteAndZapPermissionOnCachedFolder() {
        return new Object[][] {
                //canDelete, canManage, available actions
                {true, true, List.of("Refresh", "Zap", "DeleteVersions", "Delete")},
                {true, false, List.of("Refresh", "DeleteVersions", "Delete")},
                {false, true, List.of("Refresh", "Zap")},
                {false, false, List.of("Refresh")}
        };
    }

    @Test(dataProvider = "folderPathReindexScenarios")
    public void testFolderPathReindexAction(RepoType repoPkgType, String repoType,
            boolean hasPathReindex, boolean canManage, boolean shouldHaveAction) {
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType(repoType);
        tabsAndActions.setRepoPkgType(repoPkgType);
        tabsAndActions.setRepoPath(String.format("conan-local/user/pkg/1.0/channel"));
        enableFolderDownload(false);
        makeTheUserAnonymousWithReadPermissions();
        when(packageSpecification.hasPathReindex(repoPkgType)).thenReturn(hasPathReindex);
        when(authService.canManage(RepoPathFactory.create(tabsAndActions.getRepoPath().getRepoKey())))
                .thenReturn(canManage);
        populator.populateForFolder(tabsAndActions);
        List<TabOrAction> actions = tabsAndActions.getActions();
        if (shouldHaveAction) {
            assertTrue(actions.stream().anyMatch(action -> "RecalculateIndex".equals(action.getName())),
                    "Expected RecalculateIndex action for repoType=" + repoType + ", hasPathReindex=" +
                            hasPathReindex + ", canManage=" + canManage);
        } else {
            assertFalse(actions.stream().anyMatch(action -> "RecalculateIndex".equals(action.getName())),
                    "Did not expect RecalculateIndex action for repoType=" + repoType + ", hasPathReindex=" +
                            hasPathReindex + ", canManage=" + canManage);
        }
    }

    @DataProvider
    public static Object[][] folderPathReindexScenarios() {
        return new Object[][] {
                // repoPkgType, repoType, hasPathReindex, canManage, shouldHaveAction
                {RepoType.Conan, LOCAL_REPO, true, true, true},
                {RepoType.Conan, FEDERATED_REPO, true, true, true},
                {RepoType.Conan, LOCAL_REPO, true, false, false},
                {RepoType.Conan, FEDERATED_REPO, true, false, false},
                {RepoType.Conan, VIRTUAL_REPO, true, true, false},
                {RepoType.Npm, LOCAL_REPO, false, true, false},
                {RepoType.Maven, LOCAL_REPO, false, true, false},
        };
    }
}