package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions;

import com.jfrog.sysconf.SysConfig;
import org.artifactory.addon.AddonType;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.bower.BowerAddon;
import org.artifactory.addon.cargo.CargoAddon;
import org.artifactory.addon.chef.ChefAddon;
import org.artifactory.addon.composer.ComposerAddon;
import org.artifactory.addon.pub.PubAddon;
import org.artifactory.addon.puppet.PuppetAddon;
import org.artifactory.addon.swift.SwiftAddon;
import org.artifactory.addon.terraform.TerraformAddon;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.FolderInfo;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoPath;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.artifactory.mime.DockerNaming.MANIFEST_LIST_FILENAME;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions.TreeNodeTabsPopulator.EVIDENCE_TAB;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions.TreeNodeTabsPopulator.GENERAL_TAB;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions.TreeNodeTabsPopulator.PROPERTIES_TAB;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions.TreeNodeTabsPopulator.XRAY_TAB;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_ENABLED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Yevdo Abramov
 * Created on 25/10/2020
 */
public class TreeNodeTabsPopulatorTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ProjectsService projectsService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private XrayAddon xrayAddon;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private PackageSpecification packageSpecification;
    @Mock
    private RepositoryService repoService;
    @Mock
    private SysConfig sysConfig;

    private TreeNodeTabsPopulator treeNodeTabsPopulator;

    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        treeNodeTabsPopulator = new TreeNodeTabsPopulator();
        treeNodeTabsPopulator.setProjectsService(projectsService);
        treeNodeTabsPopulator.setAddonsManager(addonsManager);
        treeNodeTabsPopulator.setAuthService(authorizationService);
        treeNodeTabsPopulator.setPackageInfoProvider(packageSpecification);
        treeNodeTabsPopulator.setRepoService(repoService);

        when(addonsManager.addonByType(XrayAddon.class)).thenReturn(xrayAddon);

        ArtifactoryHome artifactoryHome = spy(ArtifactoryHome.get());
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        verifyNoMoreInteractions(projectsService, xrayAddon, authorizationService);
        mocks.close();
    }

    @Test
    public void testPopulateForFileDockerV2Info() {
        // local repo
        // given
        final String repoKey = "docker_repo";
        RepoPath repoPath = new RepoPathImpl(repoKey, "path/to/" + MANIFEST_LIST_FILENAME);

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoPath.getId());
        tabsAndActions.setRepoType("local");
        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();

        // when
        when(sysConfig.getBoolean(eq(EVIDENCE_ENABLED), anyBoolean())).thenReturn(true);
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);

        // then
        List<TabOrAction> tabs = tabsAndActions.getTabs();
        assertEquals(tabs.size(), 4);
        assertEquals(tabs.get(0).getName(), GENERAL_TAB);
        assertEquals(tabs.get(1).getName(), "DockerV2Info");
        assertEquals(tabs.get(2).getName(), PROPERTIES_TAB);
        assertEquals(tabs.get(3).getName(), EVIDENCE_TAB);

        verify(projectsService).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon).isXrayEnabled();
    }

    @Test
    public void testPopulateForAnsible() {
        final String repoKey = "ansible-local";
        RepoPath repoPath = new RepoPathImpl(repoKey, "/path/to/some.zip");

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoPath.toPath());
        tabsAndActions.setRepoType("local");
        tabsAndActions.setRepoPkgType(RepoType.Ansible);

        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        when(packageSpecification.isPackageArtifact(eq(repoPath.getRepoKey()), anyString())).thenReturn(true);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Ansible);
        Mockito.when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);

        assertTrue(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("AnsibleInfo")));
        verify(projectsService).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon).isXrayEnabled();
    }

    @Test
    public void testPopulateForHex() {
        final String repoKey = "hex-local";
        RepoPath repoPath = new RepoPathImpl(repoKey, "/path/to/some.tar");

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoPath.toPath());
        tabsAndActions.setRepoType("local");
        tabsAndActions.setRepoPkgType(RepoType.Hex);

        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        when(packageSpecification.isPackageArtifact(eq(repoPath.getRepoKey()), anyString())).thenReturn(true);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Hex);
        Mockito.when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);

        assertTrue(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("HexInfo")));
        verify(projectsService).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon).isXrayEnabled();
    }

    @Test
    public void testPopulateForAgentPlugins() {
        final String repoKey = "agentplugins-local";
        RepoPath repoPath = new RepoPathImpl(repoKey, "/path/to/some.zip");

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoPath.toPath());
        tabsAndActions.setRepoType("local");
        tabsAndActions.setRepoPkgType(RepoType.AgentPlugins);

        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        when(packageSpecification.isPackageArtifact(eq(repoPath.getRepoKey()), anyString())).thenReturn(true);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.AgentPlugins);
        Mockito.when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);

        assertTrue(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("AgentPluginsInfo")));
        verify(projectsService).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon).isXrayEnabled();
    }

    @Test
    public void testPopulateForAgentPlugins_NoTab_whenNotPackageArtifact() {
        final String repoKey = "agentplugins-local";
        RepoPath repoPath = new RepoPathImpl(repoKey, "/path/to/readme.txt");

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoPath.toPath());
        tabsAndActions.setRepoType("local");
        tabsAndActions.setRepoPkgType(RepoType.AgentPlugins);

        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        when(packageSpecification.isPackageArtifact(eq(repoPath.getRepoKey()), anyString())).thenReturn(false);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.AgentPlugins);
        Mockito.when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);

        assertFalse(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("AgentPluginsInfo")));
        verify(projectsService).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon).isXrayEnabled();
    }

    @Test
    public void testPopulateForAgentPlugins_NoTab_whenWrongPackageType() {
        final String repoKey = "hex-local";
        RepoPath repoPath = new RepoPathImpl(repoKey, "/path/to/some.zip");

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoPath.toPath());
        tabsAndActions.setRepoType("local");
        tabsAndActions.setRepoPkgType(RepoType.Hex);

        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        when(packageSpecification.isPackageArtifact(eq(repoPath.getRepoKey()), anyString())).thenReturn(true);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Hex);
        Mockito.when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);

        assertFalse(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("AgentPluginsInfo")));
        verify(projectsService).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon).isXrayEnabled();
    }

    @Test
    public void testPopulateForAlpine() {
        final String repoKey = "alpine-local";
        RepoPath repoPath = new RepoPathImpl(repoKey, "/path/to/some.apk");

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoPath.toPath());
        tabsAndActions.setRepoType("local");
        tabsAndActions.setRepoPkgType(RepoType.Alpine);

        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        when(packageSpecification.isPackageArtifact(eq(repoPath.getRepoKey()), anyString(), eq(RepoType.Alpine)))
                .thenReturn(true);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Alpine);
        Mockito.when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);

        assertTrue(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("AlpineInfo")));
        verify(projectsService).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon).isXrayEnabled();
    }

    @Test
    public void testPopulateForLuaRocks() {
        final String repoKey = "luarocks-local";
        RepoPath repoPath = new RepoPathImpl(repoKey, "/path/to/some.rock");

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoPath.toPath());
        tabsAndActions.setRepoType("local");
        tabsAndActions.setRepoPkgType(RepoType.LuaRocks);

        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        when(packageSpecification.isPackageArtifact(eq(repoPath.getRepoKey()), anyString(), eq(RepoType.LuaRocks)))
                .thenReturn(true);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.LuaRocks);
        Mockito.when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);

        assertTrue(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("RockInfo")));
        verify(projectsService).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon).isXrayEnabled();
    }

    @Test
    public void testPopulateForLuaRocksNoTabWhenNotPackageArtifact() {
        final String repoKey = "luarocks-local";
        RepoPath repoPath = new RepoPathImpl(repoKey, "/path/to/readme.txt");

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoPath.toPath());
        tabsAndActions.setRepoType("local");
        tabsAndActions.setRepoPkgType(RepoType.LuaRocks);

        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        when(packageSpecification.isPackageArtifact(eq(repoPath.getRepoKey()), anyString(), eq(RepoType.LuaRocks)))
                .thenReturn(false);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.LuaRocks);
        Mockito.when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);

        assertFalse(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("RockInfo")));
        verify(projectsService).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon).isXrayEnabled();
    }

    @Test
    public void testPopulateForLuaRocksNoTabWhenWrongPackageType() {
        final String repoKey = "luarocks-local";
        RepoPath repoPath = new RepoPathImpl(repoKey, "/path/to/some.rock");

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoPath.toPath());
        tabsAndActions.setRepoType("local");
        tabsAndActions.setRepoPkgType(RepoType.Hex);

        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        when(packageSpecification.isPackageArtifact(eq(repoPath.getRepoKey()), anyString(), eq(RepoType.LuaRocks)))
                .thenReturn(true);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Hex);
        Mockito.when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);

        assertFalse(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("RockInfo")));
        verify(projectsService).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon).isXrayEnabled();
    }

    @Test
    public void testPopulateForPypi() {
        final String repoKey = "pypi-local";
        RepoPath repoPath = new RepoPathImpl(repoKey, "/path/to/some.whl");

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoPath.toPath());
        tabsAndActions.setRepoType("local");
        tabsAndActions.setRepoPkgType(RepoType.Pypi);

        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        when(packageSpecification.isPackageArtifact(eq(repoPath.getRepoKey()), anyString(),
                                                    eq(RepoType.Pypi))).thenReturn(true);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Pypi);
        Mockito.when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);

        assertTrue(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("PyPIInfo")));
        verify(projectsService).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon).isXrayEnabled();
    }

    @Test
    public void testPopulateForNix() {
        final String repoKey = "nix-local";
        RepoPath repoPath = new RepoPathImpl(repoKey, "/path/to/folder");

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoPath.toPath());
        tabsAndActions.setRepoType("local");
        tabsAndActions.setRepoPkgType(RepoType.Nix);

        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        when(packageSpecification.isPackageArtifact(eq(repoPath.getRepoKey()), anyString())).thenReturn(true);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Nix);
        Mockito.when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        spyTreeNodeTabsPopulator.populateForFolder(tabsAndActions);

        assertTrue(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("NixInfo")));
        verify(projectsService).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon).isXrayEnabled();
    }

    @Test
    public void testPopulateForVirtualRepoFolderWithNix() {
        final String virtualRepoKey = "nix-virtual";
        final String localRepoKey = "nix-local";
        RepoPath virtualRepoPath = new RepoPathImpl(virtualRepoKey, "/path/to/folder");
        RepoPath localRepoPath = new RepoPathImpl(localRepoKey, "/path/to/folder");

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(virtualRepoPath.toPath());
        tabsAndActions.setRepoType("virtual");
        tabsAndActions.setRepoPkgType(RepoType.Nix);
        tabsAndActions.setCached(true);

        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(localRepoPath);
        when(repoService.getVirtualFolderInfo(virtualRepoPath)).thenReturn(folderInfo);
        when(packageSpecification.isPackageArtifact(eq(localRepoPath.getRepoKey()), anyString())).thenReturn(true);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getKey()).thenReturn(localRepoKey);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Nix);
        when(repoService.getLocalFederatedAndCachedRepoConfigs()).thenReturn(List.of(repoConfig));
        spyTreeNodeTabsPopulator.populateForFolder(tabsAndActions);

        assertTrue(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("NixInfo")));
        verify(repoService).getVirtualFolderInfo(virtualRepoPath);
        verify(projectsService).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
    }

    @Test
    public void testPopulateForVirtualRepoFileWithNix() {
        final String virtualRepoKey = "nix-virtual";
        final String localRepoKey = "nix-local";
        RepoPath virtualRepoPath = new RepoPathImpl(virtualRepoKey, "/path/to/file");
        RepoPath localRepoPath = new RepoPathImpl(localRepoKey, "/path/to/file");

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(virtualRepoPath.toPath());
        tabsAndActions.setRepoType("virtual");
        tabsAndActions.setRepoPkgType(RepoType.Nix);
        tabsAndActions.setCached(true);
        tabsAndActions.setType("file");

        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        FileInfo fileInfo = mock(FileInfoImpl.class);
        when(fileInfo.getRepoPath()).thenReturn(localRepoPath);
        when(repoService.getVirtualFileInfo(virtualRepoPath)).thenReturn(fileInfo);
        when(packageSpecification.isPackageArtifact(eq(localRepoPath.getRepoKey()), anyString())).thenReturn(true);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getKey()).thenReturn(localRepoKey);
        when(repoConfig.isLocal()).thenReturn(true);
        when(repoConfig.isCache()).thenReturn(false);
        when(repoService.getLocalFederatedAndCachedRepoConfigs()).thenReturn(List.of(repoConfig));
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);

        assertFalse(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("NixInfo")));
        verify(repoService).getVirtualFileInfo(virtualRepoPath);
        verify(projectsService).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon, times(0)).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon, times(0)).isXrayEnabled();
    }

    @Test
    public void testPopulateForDockerLocalFileWithEnabledEvidence() {
        // local repo
        // given
        final String repoKey = "docker_repo";
        RepoPath repoPath = new RepoPathImpl(repoKey, "path/to/" + MANIFEST_LIST_FILENAME);

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoPath.getId());
        tabsAndActions.setRepoType("local");
        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();

        // when
        when(sysConfig.getBoolean(eq(EVIDENCE_ENABLED), anyBoolean())).thenReturn(true);

        ArrayList<TabOrAction> tabs = new ArrayList<>();
        spyTreeNodeTabsPopulator.addTabsForCachedOrLocalFile(tabs, repoPath, tabsAndActions, true);
        tabs.removeIf(Objects::isNull);
        // then
        assertEquals(tabs.size(), 3);
        assertEquals(tabs.get(0).getName(), "DockerV2Info");
        assertEquals(tabs.get(1).getName(), PROPERTIES_TAB);
        assertEquals(tabs.get(2).getName(), EVIDENCE_TAB);

        verify(projectsService, times(1)).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon, times(0)).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon, times(0)).isXrayEnabled();
    }

    @Test
    public void testPopulateForDockerLocalFileWithDisabledEvidence() {
        // local repo
        // given
        final String repoKey = "docker_repo";
        RepoPath repoPath = new RepoPathImpl(repoKey, "path/to/" + MANIFEST_LIST_FILENAME);

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoPath.getId());
        tabsAndActions.setRepoType("local");
        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();

        // when
        when(sysConfig.getBoolean(eq(EVIDENCE_ENABLED), anyBoolean())).thenReturn(false);

        ArrayList<TabOrAction> tabs = new ArrayList<>();
        spyTreeNodeTabsPopulator.addTabsForCachedOrLocalFile(tabs, repoPath, tabsAndActions, true);
        tabs.removeIf(Objects::isNull);
        // then
        assertEquals(tabs.size(), 2);
        assertEquals(tabs.get(0).getName(), "DockerV2Info");
        assertEquals(tabs.get(1).getName(), PROPERTIES_TAB);

        verify(projectsService, times(1)).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon, times(0)).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon, times(0)).isXrayEnabled();
    }

    @Test
    public void testPopulateForBazelModulesRemoteCacheFile() {
        final String repoKey = "bazelmodules-remote";
        RepoPath tarFile = new RepoPathImpl(repoKey, "modules/zlib/1.3.1.bcr.5/zlib-1.3.1.tar.gz");
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType("remote");
        tabsAndActions.setRepoPkgType(RepoType.BazelModules);
        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        when(packageSpecification.isPackageArtifact(eq(tarFile.getRepoKey()), anyString())).thenReturn(true);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.BazelModules);
        Mockito.when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        tabsAndActions.setRepoPath(tarFile.toPath());
        List<TabOrAction> tabs = new ArrayList<>();
        spyTreeNodeTabsPopulator.addTabsForCachedOrLocalFile(tabs, tarFile, tabsAndActions, false);
        assertTrue(
                tabs.stream().filter(Objects::nonNull).anyMatch(tab -> tab.getName().equals("BazelModulesInfo")));
        assertTrue(
                tabs.stream().filter(Objects::nonNull).noneMatch(tab -> tab.getName().equals(EVIDENCE_TAB))
        );
        verify(projectsService, times(1)).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon, times(0)).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon, times(0)).isXrayEnabled();
    }


    @Test
    public void testPopulateForJetrainsPluginsRemoteCacheFile() {
        final String repoKey = "jetbrains-remote";
        RepoPath zipFile = new RepoPathImpl(repoKey, "/buildId/xmlId/xmlid_buildId.zip");
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType("remote");
        tabsAndActions.setRepoPkgType(RepoType.JetBrainsPlugins);
        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        when(packageSpecification.isPackageArtifact(eq(zipFile.getRepoKey()), anyString())).thenReturn(true);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.JetBrainsPlugins);
        Mockito.when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        tabsAndActions.setRepoPath(zipFile.toPath());
        List<TabOrAction> tabs = new ArrayList<>();
        spyTreeNodeTabsPopulator.addTabsForCachedOrLocalFile(tabs, zipFile, tabsAndActions, false);
        assertTrue(
                tabs.stream().filter(Objects::nonNull).anyMatch(tab -> tab.getName().equals("JetBrainsPluginsInfo")));
        verify(projectsService, times(1)).getProjectKeyOfRepository(any());
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon, times(0)).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon, times(0)).isXrayEnabled();
    }

    @Test
    public void testPopulateForMachineLearning() {
        final String repoKey = "ml-local";
        RepoPath modelRepoPath =
                new RepoPathImpl(repoKey, "/models/namespace/model-name/1.0.0/model/model_file.json");
        RepoPath datasetRepoPath =
                new RepoPathImpl(repoKey, "/datasets/namespace/dataset-name/1.0.0/dataset/dataset_file.json");

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType("local");
        tabsAndActions.setRepoPkgType(RepoType.MachineLearning);

        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        when(packageSpecification.isPackageArtifact(eq(modelRepoPath.getRepoKey()), anyString())).thenReturn(true);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.MachineLearning);
        Mockito.when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);

        tabsAndActions.setRepoPath(modelRepoPath.toPath());
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);
        assertTrue(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("MachineLearningModelInfo")));

        tabsAndActions.setRepoPath(datasetRepoPath.toPath());
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);
        assertTrue(
                tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("MachineLearningDatasetInfo")));

        verify(projectsService, times(2)).getProjectKeyOfRepository(any());
        verify(authorizationService, times(4)).canManage(any(RepoPath.class));
        verify(xrayAddon, times(2)).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon, times(2)).isXrayEnabled();
    }

    @Test
    public void testPopulateForHuggingFace() {
        final String repoKey = "ml-local";
        RepoPath modelRepoPath =
                new RepoPathImpl(repoKey,
                        "/models/namespace/model-name/1.0.0/2023-06-10T22:54:37.000Z/model_file.json");
        RepoPath datasetRepoPath =
                new RepoPathImpl(repoKey,
                        "/datasets/namespace/dataset-name/1.0.0/2023-06-10T22:54:37.000Z/dataset_file.json");

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoType("local");
        tabsAndActions.setRepoPkgType(RepoType.HuggingFaceML);

        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();
        when(packageSpecification.isPackageArtifact(eq(modelRepoPath.getRepoKey()), anyString())).thenReturn(true);
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.HuggingFaceML);
        Mockito.when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);

        tabsAndActions.setRepoPath(modelRepoPath.toPath());
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);
        assertTrue(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("HuggingfacemlModelInfo")));

        tabsAndActions.setRepoPath(datasetRepoPath.toPath());
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);
        assertTrue(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("HuggingfacemlDatasetInfo")));

        verify(projectsService, times(2)).getProjectKeyOfRepository(any());
        verify(authorizationService, times(4)).canManage(any(RepoPath.class));
        verify(xrayAddon, times(2)).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon, times(2)).isXrayEnabled();
    }

    @Test
    public void testPopulateForRepositoryFromDefaultProject() {
        // given
        final String repoKey = "my_repo";
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoKey);
        tabsAndActions.setRepoType("local");
        when(projectsService.getProjectKeyOfRepository(repoKey)).thenReturn(ProjectsService.DEFAULT_PROJECT);
        when(authorizationService.canManage(any(RepoPath.class))).thenReturn(true);
        when(xrayAddon.isXrayConfPerRepoAndIntegrationEnabled(repoKey)).thenReturn(false);

        // when
        treeNodeTabsPopulator.populateForRepository(tabsAndActions);

        // then
        assertTrue(tabsAndActions.getTabs().stream()
                .anyMatch(tab -> tab.getName().equals(TreeNodeTabsPopulator.EFFECTIVE_PERMISSION_TAB)));
        verify(projectsService).getProjectKeyOfRepository(repoKey);
        verify(authorizationService, times(2)).canManage(any(RepoPath.class));
        verify(xrayAddon).isXrayConfPerRepoAndIntegrationEnabled(repoKey);
        verify(xrayAddon).isXrayEnabled();
    }

    @Test
    public void testPopulateForRepositoryFromProjectUserProject() {
        // given
        final String repoKey = "my_repo";
        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoKey);
        tabsAndActions.setRepoType("local");
        when(projectsService.getProjectKeyOfRepository(repoKey)).thenReturn("user_project");
        when(authorizationService.canManage(any(RepoPath.class))).thenReturn(true);
        when(xrayAddon.isXrayConfPerRepoAndIntegrationEnabled(repoKey)).thenReturn(false);

        // when
        treeNodeTabsPopulator.populateForRepository(tabsAndActions);

        // then
        assertFalse(tabsAndActions.getTabs().stream()
                .anyMatch(tab -> tab.getName().equals(TreeNodeTabsPopulator.EFFECTIVE_PERMISSION_TAB)));
        verify(projectsService).getProjectKeyOfRepository(repoKey);
        verify(authorizationService).canManage(any(RepoPath.class));
        verify(xrayAddon).isXrayConfPerRepoAndIntegrationEnabled(repoKey);
        verify(xrayAddon).isXrayEnabled();
    }

    @Test(dataProvider = "testXrayDataProvider")
    public void testXray(boolean virtualSource, boolean xrayOfMinimalRequiredVersion, boolean xrayEnabled,
            boolean xrayConfPerRepoAndIntegrationEnabled, boolean handledByXray, String fileOrFolder,
            TabOrAction expected, int expectedCallsMinReqVer, int expectedCallsXrayEnabled,
            int expectedCallsConfPerRepoAndIntegEnabled, int expectedCallsHandledByXray) {
        RepoPathImpl repoPath = new RepoPathImpl("coolest-repo-ever", "path/to/file");
        when(xrayAddon.isXrayOfMinimalRequiredVersion(any()))
                .thenReturn(xrayOfMinimalRequiredVersion);
        when(xrayAddon.isXrayEnabled()).thenReturn(xrayEnabled);
        when(xrayAddon.isXrayConfPerRepoAndIntegrationEnabled(repoPath.getRepoKey()))
                .thenReturn(xrayConfPerRepoAndIntegrationEnabled);
        when(xrayAddon.isHandledByXray(repoPath)).thenReturn(handledByXray);
        TabsAndActions tabsAndActions = mock(TabsAndActions.class);
        when(tabsAndActions.getType()).thenReturn(fileOrFolder);
        TabOrAction tabOrAction = treeNodeTabsPopulator.xray(repoPath, virtualSource, tabsAndActions);
        assertEquals(tabOrAction, expected);
        verify(xrayAddon, times(expectedCallsMinReqVer)).isXrayOfMinimalRequiredVersion(any());
        verify(xrayAddon, times(expectedCallsXrayEnabled)).isXrayEnabled();
        verify(xrayAddon, times(expectedCallsConfPerRepoAndIntegEnabled))
                .isXrayConfPerRepoAndIntegrationEnabled(repoPath.getRepoKey());
        verify(xrayAddon, times(expectedCallsHandledByXray)).isHandledByXray(repoPath);
    }

    @DataProvider
    private Object[][] testXrayDataProvider() {
        TabOrAction tabOrAction = new TabOrAction(XRAY_TAB);
        return new Object[][] {
                {true, true, true, true, true, "file", null, 0, 0, 0, 0},
                {true, true, true, true, false, "file", null, 0, 0, 0, 0},
                {true, true, true, false, true, "file", null, 0, 0, 0, 0},
                {true, true, true, false, false, "file", null, 0, 0, 0, 0},
                {true, true, false, true, true, "file", null, 0, 0, 0, 0},
                {true, true, false, true, false, "file", null, 0, 0, 0, 0},
                {true, true, false, false, true, "file", null, 0, 0, 0, 0},
                {true, true, false, false, false, "file", null, 0, 0, 0, 0},
                {true, false, true, true, true, "file", null, 0, 0, 0, 0},
                {true, false, true, true, false, "file", null, 0, 0, 0, 0},
                {true, false, true, false, true, "file", null, 0, 0, 0, 0},
                {true, false, true, false, false, "file", null, 0, 0, 0, 0},
                {true, false, false, true, true, "file", null, 0, 0, 0, 0},
                {true, false, false, true, false, "file", null, 0, 0, 0, 0},
                {true, false, false, false, true, "file", null, 0, 0, 0, 0},
                {true, false, false, false, false, "file", null, 0, 0, 0, 0},
                {false, true, true, true, true, "file", tabOrAction, 1, 1, 0, 0},
                {false, true, true, true, false, "file", tabOrAction, 1, 1, 0, 0},
                {false, true, true, false, true, "file", tabOrAction, 1, 1, 0, 0},
                {false, true, true, false, false, "file", tabOrAction, 1, 1, 0, 0},
                {false, true, false, true, true, "file", tabOrAction, 0, 1, 1, 1},
                {false, true, false, true, false, "file", null, 0, 1, 1, 1},
                {false, true, false, false, true, "file", null, 0, 1, 1, 0},
                {false, true, false, false, false, "file", null, 0, 1, 1, 0},
                {false, false, true, true, true, "file", tabOrAction, 1, 1, 1, 1},
                {false, false, true, true, false, "file", null, 1, 1, 1, 1},
                {false, false, true, false, true, "file", null, 1, 1, 1, 0},
                {false, false, true, false, false, "file", null, 1, 1, 1, 0},
                {false, false, false, true, true, "file", tabOrAction, 0, 1, 1, 1},
                {false, false, false, true, false, "file", null, 0, 1, 1, 1},
                {false, false, false, false, true, "file", null, 0, 1, 1, 0},
                {false, false, false, false, false, "file", null, 0, 1, 1, 0},
                {true, true, true, true, true, "folder", null, 0, 0, 0, 0},
                {true, true, true, true, false, "folder", null, 0, 0, 0, 0},
                {true, true, true, false, true, "folder", null, 0, 0, 0, 0},
                {true, true, true, false, false, "folder", null, 0, 0, 0, 0},
                {true, true, false, true, true, "folder", null, 0, 0, 0, 0},
                {true, true, false, true, false, "folder", null, 0, 0, 0, 0},
                {true, true, false, false, true, "folder", null, 0, 0, 0, 0},
                {true, true, false, false, false, "folder", null, 0, 0, 0, 0},
                {true, false, true, true, true, "folder", null, 0, 0, 0, 0},
                {true, false, true, true, false, "folder", null, 0, 0, 0, 0},
                {true, false, true, false, true, "folder", null, 0, 0, 0, 0},
                {true, false, true, false, false, "folder", null, 0, 0, 0, 0},
                {true, false, false, true, true, "folder", null, 0, 0, 0, 0},
                {true, false, false, true, false, "folder", null, 0, 0, 0, 0},
                {true, false, false, false, true, "folder", null, 0, 0, 0, 0},
                {true, false, false, false, false, "folder", null, 0, 0, 0, 0},
                {false, true, true, true, true, "folder", null, 1, 1, 0, 0},
                {false, true, true, true, false, "folder", null, 1, 1, 0, 0},
                {false, true, true, false, true, "folder", null, 1, 1, 0, 0},
                {false, true, true, false, false, "folder", null, 1, 1, 0, 0},
                {false, true, false, true, true, "folder", tabOrAction, 0, 1, 1, 1},
                {false, true, false, true, false, "folder", null, 0, 1, 1, 1},
                {false, true, false, false, true, "folder", null, 0, 1, 1, 0},
                {false, true, false, false, false, "folder", null, 0, 1, 1, 0},
                {false, false, true, true, true, "folder", tabOrAction, 1, 1, 1, 1},
                {false, false, true, true, false, "folder", null, 1, 1, 1, 1},
                {false, false, true, false, true, "folder", null, 1, 1, 1, 0},
                {false, false, true, false, false, "folder", null, 1, 1, 1, 0},
                {false, false, false, true, true, "folder", tabOrAction, 0, 1, 1, 1},
                {false, false, false, true, false, "folder", null, 0, 1, 1, 1},
                {false, false, false, false, true, "folder", null, 0, 1, 1, 0},
                {false, false, false, false, false, "folder", null, 0, 1, 1, 0}
        };
    }

    @Test
    public void testPopulateForDebian() {
        String[] debianFileExtensions = {".deb", ".ddeb", ".dsc"};

        for (String extension : debianFileExtensions) {
            final String repoKey = "debian-local";
            RepoPath repoPath = new RepoPathImpl(repoKey, "/path/to/package" + extension);

            TabsAndActions tabsAndActions = new TabsAndActions();
            tabsAndActions.setRepoPath(repoPath.toPath());
            tabsAndActions.setRepoType("local");
            tabsAndActions.setRepoPkgType(RepoType.Debian);
            TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();

            LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
            when(repoConfig.getPackageType()).thenReturn(RepoType.Debian);
            when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);

            when(addonsManager.isAddonSupported(AddonType.DEBIAN)).thenReturn(true);
            spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);

            assertTrue(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("DebianInfo")),
                    "DebianInfo tab should be present for " + extension + " files");
        }

        final String repoKey = "debian-local";
        RepoPath repoPath = new RepoPathImpl(repoKey, "/path/to/package.txt");

        TabsAndActions tabsAndActions = new TabsAndActions();
        tabsAndActions.setRepoPath(repoPath.toPath());
        tabsAndActions.setRepoType("local");
        tabsAndActions.setRepoPkgType(RepoType.Debian);

        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = mockTreeNodeTabsPopulator();

        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Debian);
        when(repoService.localFederatedOrCachedRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        when(addonsManager.isAddonSupported(AddonType.DEBIAN)).thenReturn(true);
        spyTreeNodeTabsPopulator.populateForFile(tabsAndActions);

        assertFalse(tabsAndActions.getTabs().stream().anyMatch(tab -> tab.getName().equals("DebianInfo")),
                "DebianInfo tab should not be present for non-Debian files");

        verify(projectsService, times(4)).getProjectKeyOfRepository(any());
        verify(authorizationService, times(8)).canManage(any(RepoPath.class));
        verify(xrayAddon, times(4)).isXrayConfPerRepoAndIntegrationEnabled(any());
        verify(xrayAddon, times(4)).isXrayEnabled();
    }

    private TreeNodeTabsPopulator mockTreeNodeTabsPopulator() {
        TreeNodeTabsPopulator spyTreeNodeTabsPopulator = spy(treeNodeTabsPopulator);
        spyTreeNodeTabsPopulator.init();

        when(addonsManager.addonByType(BowerAddon.class)).thenReturn(mock(BowerAddon.class));
        when(addonsManager.addonByType(ComposerAddon.class)).thenReturn(mock(ComposerAddon.class));
        when(addonsManager.addonByType(ChefAddon.class)).thenReturn(mock(ChefAddon.class));
        when(addonsManager.addonByType(PuppetAddon.class)).thenReturn(mock(PuppetAddon.class));
        when(addonsManager.addonByType(CargoAddon.class)).thenReturn(mock(CargoAddon.class));
        when(addonsManager.addonByType(PubAddon.class)).thenReturn(mock(PubAddon.class));
        when(addonsManager.addonByType(SwiftAddon.class)).thenReturn(mock(SwiftAddon.class));
        when(addonsManager.addonByType(TerraformAddon.class)).thenReturn(mock(TerraformAddon.class));
        when(repoService.localFederatedCachedRepoDescriptorByKey(any())).thenReturn(mock(LocalRepoDescriptor.class));
        return spyTreeNodeTabsPopulator;
    }
}
