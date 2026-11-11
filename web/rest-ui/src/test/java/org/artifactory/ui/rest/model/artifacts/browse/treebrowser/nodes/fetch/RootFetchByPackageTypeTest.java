package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.TreeFilter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.RepositoryNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.VirtualRemoteRepositoryNode;
import org.jfrog.commons.continues.ContinueResult;
import org.jfrog.commons.continues.FetchFunction;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.artifactory.common.ConstantValues.treeBrowserCheckOnlyReadPermissionOnRootLevel;
import static org.artifactory.common.ConstantValues.treeBrowserImprovedShowRepoInTreeEnabled;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.descriptor.repo.RepoType.ReleaseBundles;
import static org.artifactory.repo.RepoDetailsType.LOCAL_REPO;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE_REPO;
import static org.artifactory.repo.system.SystemRepos.RELEASE_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.RootFetchStrategy.sortUpAppVersionsRepos;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.RootFetchStrategy.sortUpReleaseBundleV2Repos;
import static org.artifactory.util.bundles.BundleConfigurationUtil.APP_VERSIONS_REPO;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Omri Ziv
 */
public class RootFetchByPackageTypeTest extends RootFetchStrategyTest {

    @Test
    public void testByType() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setPackageTypes(asList(RepoType.Maven, RepoType.Docker));
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        assertEquals(fetchFunctions.size(), 5);
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 5L);
        assertEquals(fetchFunctions.get(4).getOriginalSize().longValue(), 10L);

        ContinueResult<? extends RestModel> continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 20);
        assertEquals(continueResult1.getData().size(), 5);
        ContinueResult<? extends RestModel> continueResult2 = fetchFunctions.get(4).getFunction().apply(0, 20);
        assertEquals(continueResult2.getData().size(), 10, 10);
    }

    @Test(dataProvider = "provideProjectKeys")
    public void testByRepoType(String project) {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setProjectKey(project);
        treeFilter.setPackageTypes(List.of(RepoType.Npm));
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        assertEquals(fetchFunctions.size(), 4);

        ContinueResult<? extends RestModel> continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 20);
        assertEquals(continueResult1.getData().size(), 9);
        assertTrue(continueResult1.getData().get(7) instanceof VirtualRemoteRepositoryNode);
        assertTrue(continueResult1.getData().get(8) instanceof VirtualRemoteRepositoryNode);
        long virtualRemotes = continueResult1.getData().stream()
                .filter(repo -> repo instanceof VirtualRemoteRepositoryNode).count();
        assertEquals(virtualRemotes, 2);
    }

    @Test
    public void testByTypeNoFilter() {
        createMocks();

        TreeFilter treeFilter = new TreeFilter();
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();

        assertEquals(fetchFunctions.size(), 7);
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 5L);
        assertEquals(fetchFunctions.get(4).getOriginalSize().longValue(), 10L);
        assertEquals(fetchFunctions.get(5).getOriginalSize().longValue(), 9L);

        ContinueResult<RepositoryNode> continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 20);
        assertEquals(continueResult1.getData().size(), 5);
        ContinueResult<RepositoryNode> continueResult2 = fetchFunctions.get(4).getFunction().apply(0, 20);
        assertEquals(continueResult2.getData().size(), 10);
        ContinueResult<RepositoryNode> continueResult3 = fetchFunctions.get(5).getFunction().apply(0, 20);
        assertEquals(continueResult3.getData().size(), 9);

    }

    @Test
    public void testByTypeIncludingLocalAndVirtual() {
        createMocksWithLocalAndVirtual();

        TreeFilter treeFilter = new TreeFilter();
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();

        assertEquals(fetchFunctions.size(), 6);
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 9L);
        assertEquals(fetchFunctions.get(4).getOriginalSize().longValue(), 13L);
        assertEquals(fetchFunctions.get(5).getOriginalSize().longValue(), 8L);

        ContinueResult<RepositoryNode> continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 20);
        assertEquals(continueResult1.getData().size(), 9);
        ContinueResult<RepositoryNode> continueResult2 = fetchFunctions.get(4).getFunction().apply(0, 20);
        assertEquals(continueResult2.getData().size(), 13);
        ContinueResult<RepositoryNode> continueResult3 = fetchFunctions.get(5).getFunction().apply(0, 20);
        assertEquals(continueResult3.getData().size(), 8);

    }

    @Test
    public void testByTypeIncludingLocalAndVirtualCheckSkipAndLimit() {
        createMocksWithLocalAndVirtual();

        TreeFilter treeFilter = new TreeFilter();
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();

        assertEquals(fetchFunctions.size(), 6);
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 9L);
        assertEquals(fetchFunctions.get(4).getOriginalSize().longValue(), 13L);
        assertEquals(fetchFunctions.get(5).getOriginalSize().longValue(), 8L);

        ContinueResult<RepositoryNode> continueResult1 = fetchFunctions.get(3).getFunction().apply(2, 1);
        assertEquals(continueResult1.getData().size(), 1);
        ContinueResult<RepositoryNode> continueResult2 = fetchFunctions.get(4).getFunction().apply(0, 3);
        assertEquals(continueResult2.getData().size(), 3);
        ContinueResult<RepositoryNode> continueResult3 = fetchFunctions.get(5).getFunction().apply(0, 4);
        assertEquals(continueResult3.getData().size(), 4);
        ContinueResult<RepositoryNode> continueResult4 = fetchFunctions.get(5).getFunction().apply(3, 51);
        assertEquals(continueResult4.getData().size(), 5);

    }

    @Test
    public void testByTypeOnlyVirtualFilter() {
        createMocksWithLocalAndVirtual();

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setRepositoryTypes(Collections.singletonList(RepositoryType.VIRTUAL));
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();

        assertEquals(fetchFunctions.size(), 6);
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 9L);
        assertEquals(fetchFunctions.get(4).getOriginalSize().longValue(), 13L);
        assertEquals(fetchFunctions.get(5).getOriginalSize().longValue(), 8L);

        ContinueResult<RepositoryNode> continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 20);
        assertEquals(continueResult1.getData().size(), 4);
        ContinueResult<RepositoryNode> continueResult2 = fetchFunctions.get(4).getFunction().apply(0, 20);
        assertEquals(continueResult2.getData().size(), 3);
        ContinueResult<RepositoryNode> continueResult3 = fetchFunctions.get(5).getFunction().apply(0, 20);
        assertEquals(continueResult3.getData().size(), 1);
    }

    @Test
    public void testByTypeOnlyRemoteExpectNone() {
        createMocksWithLocalAndVirtual();

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setRepositoryTypes(Collections.singletonList(RepositoryType.REMOTE));
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();

        assertEquals(fetchFunctions.size(), 6);
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 9L);
        assertEquals(fetchFunctions.get(4).getOriginalSize().longValue(), 13L);
        assertEquals(fetchFunctions.get(5).getOriginalSize().longValue(), 8L);

        ContinueResult<RepositoryNode> continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 20);
        assertEquals(continueResult1.getData().size(), 0);
        ContinueResult<RepositoryNode> continueResult2 = fetchFunctions.get(4).getFunction().apply(0, 20);
        assertEquals(continueResult2.getData().size(), 0);
        ContinueResult<RepositoryNode> continueResult3 = fetchFunctions.get(5).getFunction().apply(0, 20);
        assertEquals(continueResult3.getData().size(), 0);
    }

    @Test
    public void testByTypeFilterByRepoKey() {
        createMocksWithLocalAndVirtual();

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setByRepoKey("00");
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();

        assertEquals(fetchFunctions.size(), 6);
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 9L);
        assertEquals(fetchFunctions.get(4).getOriginalSize().longValue(), 13L);
        assertEquals(fetchFunctions.get(5).getOriginalSize().longValue(), 8L);

        ContinueResult<RepositoryNode> continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 20);
        assertEquals(continueResult1.getData().size(), 9);
        ContinueResult<RepositoryNode> continueResult2 = fetchFunctions.get(4).getFunction().apply(0, 20);
        assertEquals(continueResult2.getData().size(), 12);
        ContinueResult<RepositoryNode> continueResult3 = fetchFunctions.get(5).getFunction().apply(0, 20);
        assertEquals(continueResult3.getData().size(), 8);

        treeFilter.setByRepoKey("001");
        fetchFunctions = rootFetchByPackageType.getFetchFunctions();

        assertEquals(fetchFunctions.size(), 6);
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 9L);
        assertEquals(fetchFunctions.get(4).getOriginalSize().longValue(), 13L);
        assertEquals(fetchFunctions.get(5).getOriginalSize().longValue(), 8L);

        continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 20);
        assertEquals(continueResult1.getData().size(), 2);
        continueResult2 = fetchFunctions.get(4).getFunction().apply(0, 20);
        assertEquals(continueResult2.getData().size(), 2);
        continueResult3 = fetchFunctions.get(5).getFunction().apply(0, 20);
        assertEquals(continueResult3.getData().size(), 2);
    }

    @Test
    public void testSortUpReleaseBundleV2Repos() {
        @SuppressWarnings("unchecked")
        ContinueResult<RepositoryNode> result = mock(ContinueResult.class);
        RepositoryNode rbv1Node = new RepositoryNode("release-bundles", ReleaseBundles, RELEASE_BUNDLE_REPO);
        RepositoryNode rbv2Node = new RepositoryNode(DEFAULT_RBV2_REPO, ReleaseBundles, LOCAL_REPO);
        RepositoryNode rbv2ProjectNode = new RepositoryNode("A-" + DEFAULT_RBV2_REPO, ReleaseBundles, LOCAL_REPO);
        RepositoryNode someNode = new RepositoryNode("some-generic-repo", Generic, LOCAL_REPO);
        List<RepositoryNode> data = new LinkedList<>(asList(rbv2ProjectNode, rbv2Node, rbv1Node, someNode));
        when(result.getData()).thenReturn(data);
        sortUpReleaseBundleV2Repos(result);
        assertEquals(new LinkedList<>(asList(rbv1Node, rbv2Node, rbv2ProjectNode, someNode)), data);
    }

    @Test
    public void testSortUpAppVersionsRepos() {
        @SuppressWarnings("unchecked")
        ContinueResult<RepositoryNode> result = mock(ContinueResult.class);
        RepositoryNode appVersionsNode = new RepositoryNode("A-" + APP_VERSIONS_REPO, ReleaseBundles, LOCAL_REPO);
        RepositoryNode rbv2Node = new RepositoryNode(DEFAULT_RBV2_REPO, ReleaseBundles, LOCAL_REPO);
        RepositoryNode someNode = new RepositoryNode("some-generic-repo", Generic, LOCAL_REPO);
        List<RepositoryNode> data = new LinkedList<>(asList(rbv2Node, someNode, appVersionsNode));
        when(result.getData()).thenReturn(data);
        sortUpAppVersionsRepos(result);
        assertEquals(new LinkedList<>(asList(appVersionsNode, rbv2Node, someNode)), data);
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeFeatureDisabled() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled))
                .thenReturn(false);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("Maven-repo005");
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(4).getFunction().apply(0, 3);
        assertEquals(result.getData().size(), 3);
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeWithBlankMustInclude() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled))
                .thenReturn(true);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("");
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(4).getFunction().apply(0, 3);
        assertEquals(result.getData().size(), 3);
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeWithNonZeroContinueIndex() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled))
                .thenReturn(true);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("Maven-repo005");
        treeFilter.setContinueIndex(5L);
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(4).getFunction().apply(0, 3);
        assertEquals(result.getData().size(), 3);
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeWithSystemRepos() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled))
                .thenReturn(true);

        TreeFilter treeFilter1 = new TreeFilter();
        treeFilter1.setMustInclude(SUPPORT_BUNDLE_REPO_KEY);
        RootFetchByPackageType rootFetch1 = new RootFetchByPackageType(treeFilter1);
        List<FetchFunction<RepositoryNode>> fetchFunctions1 = rootFetch1.getFetchFunctions();
        ContinueResult<RepositoryNode> result1 = fetchFunctions1.get(4).getFunction().apply(0, 3);
        assertEquals(result1.getData().size(), 3);

        TreeFilter treeFilter2 = new TreeFilter();
        treeFilter2.setMustInclude(TRASH_REPO_KEY);
        RootFetchByPackageType rootFetch2 = new RootFetchByPackageType(treeFilter2);
        List<FetchFunction<RepositoryNode>> fetchFunctions2 = rootFetch2.getFetchFunctions();
        ContinueResult<RepositoryNode> result2 = fetchFunctions2.get(4).getFunction().apply(0, 3);
        assertEquals(result2.getData().size(), 3);

        TreeFilter treeFilter3 = new TreeFilter();
        treeFilter3.setMustInclude(RELEASE_BUNDLE_REPO_KEY);
        RootFetchByPackageType rootFetch3 = new RootFetchByPackageType(treeFilter3);
        List<FetchFunction<RepositoryNode>> fetchFunctions3 = rootFetch3.getFetchFunctions();
        ContinueResult<RepositoryNode> result3 = fetchFunctions3.get(4).getFunction().apply(0, 3);
        assertEquals(result3.getData().size(), 3);
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeRepoNotFound() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled))
                .thenReturn(true);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("non-existent-repo");
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(4).getFunction().apply(0, 3);
        // When repo not found, returns all repos
        assertEquals(result.getData().size(), 10);
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeRepoFound() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled))
                .thenReturn(true);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("Maven-repo005");
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(4).getFunction().apply(0, 3);

        assertTrue(result.getData().size() >= 5);
        boolean repoFound = result.getData().stream()
                .anyMatch(node -> "Maven-repo005".equals(node.getRepoKey()));
        assertTrue(repoFound, "Maven-repo005 should be included in results");
    }


    @Test(dataProvider = "booleanProvider")
    public void testDescriptorFilterSpyWithReflection(boolean onlyReadPermission) throws Exception {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserCheckOnlyReadPermissionOnRootLevel))
                .thenReturn(onlyReadPermission);
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setPackageTypes(asList(RepoType.Maven));
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);
        Field descriptorFilterField = RootFetchStrategy.class.getDeclaredField("descriptorFilter");
        descriptorFilterField.setAccessible(true);
        DescriptorFilter originalDescriptorFilter =
                (DescriptorFilter) descriptorFilterField.get(rootFetchByPackageType);
        DescriptorFilter spyDescriptorFilter = spy(originalDescriptorFilter);
        descriptorFilterField.set(rootFetchByPackageType, spyDescriptorFilter);
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        fetchFunctions.get(3).getFunction().apply(0, 20);
        Set<ArtifactoryPermission> expectedPermissions = onlyReadPermission ?
                Set.of(ArtifactoryPermission.READ) : Set.of(ArtifactoryPermission.values());
        verify(spyDescriptorFilter, times(10)).accept(any(RepoConfig.class), eq(expectedPermissions));
    }

    @Test
    public void testByType_withInactiveFilter_showsVirtualRepos() {
        createMocksWithLocalAndVirtual();
        // Inherited default predicate from RootFetchStrategyTest is repo -> true.

        TreeFilter treeFilter = new TreeFilter();
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();

        // Counts include virtuals: Docker 5L+4V=9, Maven 10L+3V=13, Npm 7L+1V=8.
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 9L);
        assertEquals(fetchFunctions.get(4).getOriginalSize().longValue(), 13L);
        assertEquals(fetchFunctions.get(5).getOriginalSize().longValue(), 8L);

        ContinueResult<RepositoryNode> mavenResult = fetchFunctions.get(4).getFunction().apply(0, 20);
        assertEquals(mavenResult.getData().size(), 13);
        boolean hasMavenVirtual = mavenResult.getData().stream()
                .anyMatch(node -> node.getRepoKey().contains("virt-repo"));
        assertTrue(hasMavenVirtual, "Default pass-through predicate should keep virtual repos visible");
    }

    @Test
    public void testByType_withActiveFilter_hidesProjectScopedVirtualRepos() {
        createMocksWithLocalAndVirtual();
        when(projectsService.createVirtualReposVisibilityFilter(DEFAULT_PROJECT))
                .thenReturn(repo -> !(repo instanceof VirtualRepoConfig));

        TreeFilter treeFilter = new TreeFilter();
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();

        // Post-filter counts (locals only): Docker 5, Maven 10, Npm 7.
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 5L);
        assertEquals(fetchFunctions.get(4).getOriginalSize().longValue(), 10L);
        assertEquals(fetchFunctions.get(5).getOriginalSize().longValue(), 7L);

        ContinueResult<RepositoryNode> dockerResult = fetchFunctions.get(3).getFunction().apply(0, 20);
        assertEquals(dockerResult.getData().size(), 5);
        ContinueResult<RepositoryNode> mavenResult = fetchFunctions.get(4).getFunction().apply(0, 20);
        assertEquals(mavenResult.getData().size(), 10);
        ContinueResult<RepositoryNode> npmResult = fetchFunctions.get(5).getFunction().apply(0, 20);
        assertEquals(npmResult.getData().size(), 7);

        boolean noVirtualKeyLeaked = Stream.of(dockerResult, mavenResult, npmResult)
                .flatMap(result -> result.getData().stream())
                .noneMatch(node -> node.getRepoKey().contains("virt-repo"));
        assertTrue(noVirtualKeyLeaked, "Active filter should hide all virtual repos across all package types");
    }

    @Test
    public void testByType_withActiveFilter_paginationIsConsistentWhenVirtualReposScatteredAcrossList() {
        createMocksWithLocalAndVirtual();
        // Mixed list with virtuals at head, middle, and tail to exercise all skip-boundary paths.
        List<RepoConfig> scatteredMavenConfigs = List.of(
                VirtualRepoConfig.create("a-virt-head", RepoType.Maven),
                LocalRepoConfig.create("b-local-1", RepoType.Maven),
                LocalRepoConfig.create("c-local-2", RepoType.Maven),
                VirtualRepoConfig.create("d-virt-mid", RepoType.Maven),
                LocalRepoConfig.create("e-local-3", RepoType.Maven),
                LocalRepoConfig.create("f-local-4", RepoType.Maven),
                VirtualRepoConfig.create("g-virt-tail", RepoType.Maven)
        );
        when(repoService.getRepoConfigsByPackageTypeAndProject(RepoType.Maven, DEFAULT_PROJECT))
                .thenReturn(scatteredMavenConfigs);
        when(projectsService.createVirtualReposVisibilityFilter(DEFAULT_PROJECT))
                .thenReturn(repo -> !(repo instanceof VirtualRepoConfig));

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setPackageTypes(List.of(RepoType.Maven));
        RootFetchByPackageType rootFetchByPackageType = new RootFetchByPackageType(treeFilter);
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();

        // 3 super functions + 1 Maven entry. Maven count is the 4 surviving locals.
        assertEquals(fetchFunctions.size(), 4);
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 4L);

        ContinueResult<RepositoryNode> page1 = fetchFunctions.get(3).getFunction().apply(0, 2);
        assertEquals(page1.getData().size(), 2);
        assertEquals(page1.getData().get(0).getRepoKey(), "b-local-1");
        assertEquals(page1.getData().get(1).getRepoKey(), "c-local-2");

        ContinueResult<RepositoryNode> page2 = fetchFunctions.get(3).getFunction().apply(2, 2);
        assertEquals(page2.getData().size(), 2);
        assertEquals(page2.getData().get(0).getRepoKey(), "e-local-3");
        assertEquals(page2.getData().get(1).getRepoKey(), "f-local-4");

        ContinueResult<RepositoryNode> page3 = fetchFunctions.get(3).getFunction().apply(4, 2);
        assertEquals(page3.getData().size(), 0);

        boolean noVirtualKeyLeaked = Stream.of(page1, page2, page3)
                .flatMap(result -> result.getData().stream())
                .noneMatch(node -> node.getRepoKey().contains("virt"));
        assertTrue(noVirtualKeyLeaked,
                "Scattered virtual repos at head/middle/tail must not leak into any paginated result");
    }

    @DataProvider
    public Object[][] booleanProvider() {
        return new Object[][] {
                {true},
                {false}
        };
    }
}
