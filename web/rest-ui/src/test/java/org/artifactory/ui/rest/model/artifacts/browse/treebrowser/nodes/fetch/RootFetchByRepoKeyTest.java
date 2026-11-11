package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch;

import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoBaseDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.TreeFilter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.RepositoryNode;
import org.jfrog.commons.continues.ContinueResult;
import org.jfrog.commons.continues.FetchFunction;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.common.ConstantValues.treeBrowserCheckOnlyReadPermissionOnRootLevel;
import static org.artifactory.common.ConstantValues.treeBrowserImprovedShowRepoInTreeEnabled;
import static org.artifactory.repo.system.SystemRepos.RELEASE_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Omri Ziv
 */
public class RootFetchByRepoKeyTest extends RootFetchStrategyTest {

    @Test
    public void testByKeyOnDefaultContext() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        RootFetchByRepoKey rootFetchByRepoKey = new RootFetchByRepoKey(treeFilter);
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByRepoKey.getFetchFunctions();
        assertEquals(fetchFunctions.size(), 8);
        assertEquals(fetchFunctions.get(5).getOriginalSize().longValue(), 5L);
        assertEquals(fetchFunctions.get(6).getOriginalSize().longValue(), 10L);

        ContinueResult<RepositoryNode> continueResult1 = fetchFunctions.get(5).getFunction().apply(0, 20);
        assertEquals(continueResult1.getData().size(), 5);
        ContinueResult<RepositoryNode> continueResult2 = fetchFunctions.get(6).getFunction().apply(0, 20);
        assertEquals(continueResult2.getData().size(), 10);
    }

    @Test
    public void testByKeyOnProjectContext() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setProjectKey(PROJECT_A);
        RootFetchByRepoKey rootFetchByRepoKey = new RootFetchByRepoKey(treeFilter);
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByRepoKey.getFetchFunctions();
        assertEquals(fetchFunctions.size(), 4);
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 7L);
        ContinueResult<RepositoryNode> continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 5);
        assertEquals(continueResult1.getData().size(), 5);
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeFeatureDisabled() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled))
                .thenReturn(false);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("Maven-repo001");
        RootFetchByRepoKey rootFetchByRepoKey = new RootFetchByRepoKey(treeFilter);

        // When feature is disabled, limit should not be adjusted
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByRepoKey.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(6).getFunction().apply(0, 3);
        assertEquals(result.getData().size(), 3); // Should respect original limit
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeWithBlankMustInclude() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled))
                .thenReturn(true);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude(""); // Blank mustInclude
        RootFetchByRepoKey rootFetchByRepoKey = new RootFetchByRepoKey(treeFilter);

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByRepoKey.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(6).getFunction().apply(0, 3);
        assertEquals(result.getData().size(), 3); // Should respect original limit
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeWithNonZeroContinueIndex() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled))
                .thenReturn(true);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("Maven-repo001");
        treeFilter.setContinueIndex(1L); // Non-zero continue index
        RootFetchByRepoKey rootFetchByRepoKey = new RootFetchByRepoKey(treeFilter);

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByRepoKey.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(6).getFunction().apply(0, 3);
        assertEquals(result.getData().size(), 3); // Should respect original limit
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeWithSystemRepos() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled))
                .thenReturn(true);

        // Test with SUPPORT_BUNDLE_REPO_KEY
        TreeFilter treeFilter1 = new TreeFilter();
        treeFilter1.setMustInclude(SUPPORT_BUNDLE_REPO_KEY);
        RootFetchByRepoKey rootFetchByRepoKey1 = new RootFetchByRepoKey(treeFilter1);

        List<FetchFunction<RepositoryNode>> fetchFunctions1 = rootFetchByRepoKey1.getFetchFunctions();
        ContinueResult<RepositoryNode> result1 = fetchFunctions1.get(6).getFunction().apply(0, 3);
        assertEquals(result1.getData().size(), 3); // Should respect original limit

        // Test with TRASH_REPO_KEY
        TreeFilter treeFilter2 = new TreeFilter();
        treeFilter2.setMustInclude(TRASH_REPO_KEY);
        RootFetchByRepoKey rootFetchByRepoKey2 = new RootFetchByRepoKey(treeFilter2);

        List<FetchFunction<RepositoryNode>> fetchFunctions2 = rootFetchByRepoKey2.getFetchFunctions();
        ContinueResult<RepositoryNode> result2 = fetchFunctions2.get(6).getFunction().apply(0, 3);
        assertEquals(result2.getData().size(), 3); // Should respect original limit

        // Test with RELEASE_BUNDLE_REPO_KEY
        TreeFilter treeFilter3 = new TreeFilter();
        treeFilter3.setMustInclude(RELEASE_BUNDLE_REPO_KEY);
        RootFetchByRepoKey rootFetchByRepoKey3 = new RootFetchByRepoKey(treeFilter3);

        List<FetchFunction<RepositoryNode>> fetchFunctions3 = rootFetchByRepoKey3.getFetchFunctions();
        ContinueResult<RepositoryNode> result3 = fetchFunctions3.get(6).getFunction().apply(0, 3);
        assertEquals(result3.getData().size(), 3); // Should respect original limit
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeRepoNotFound() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled))
                .thenReturn(true);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("non-existent-repo");
        RootFetchByRepoKey rootFetchByRepoKey = new RootFetchByRepoKey(treeFilter);

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByRepoKey.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(6).getFunction().apply(0, 3);
        // When repo is not found, should return all available repos in that fetch function
        assertTrue(result.getData().size() >= 3);
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeRepoFound() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled))
                .thenReturn(true);

        TreeFilter treeFilter = new TreeFilter();
        // Test with a repo that would normally be beyond the limit
        // Using a small limit to ensure the mustInclude feature extends it
        // Note: RepoType.Maven.getDisplayName() returns "Maven" (capitalized)
        treeFilter.setMustInclude("Maven-repo003");
        RootFetchByRepoKey rootFetchByRepoKey = new RootFetchByRepoKey(treeFilter);

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByRepoKey.getFetchFunctions();
        // Function index 6 should be 'm' character group containing maven repos
        ContinueResult<RepositoryNode> result = fetchFunctions.get(6).getFunction().apply(0, 2);

        // The key assertion: Maven-repo003 should be included even though limit is only 2
        // and it's at index 2 (0-based), which would normally be excluded
        assertTrue(result.getData().stream().anyMatch(node -> "Maven-repo003".equals(node.getRepoKey())),
                "Maven-repo003 should be included in results when specified as mustInclude");

        // Verify we got more than the original limit of 2 due to adjustment
        assertTrue(result.getData().size() >= 2, "Should have at least the original limit of repos");
    }


    @Test(dataProvider = "booleanProvider")
    public void testDescriptorFilterSpyWithReflection(boolean onlyReadPermission) throws Exception {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserCheckOnlyReadPermissionOnRootLevel))
                .thenReturn(onlyReadPermission);
        RootFetchByRepoKey rootFetchByRepoKey = new RootFetchByRepoKey(new TreeFilter());
        Field descriptorFilterField = RootFetchStrategy.class.getDeclaredField("descriptorFilter");
        descriptorFilterField.setAccessible(true);
        DescriptorFilter originalDescriptorFilter = (DescriptorFilter) descriptorFilterField.get(rootFetchByRepoKey);
        DescriptorFilter spyDescriptorFilter = spy(originalDescriptorFilter);
        descriptorFilterField.set(rootFetchByRepoKey, spyDescriptorFilter);
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByRepoKey.getFetchFunctions();
        fetchFunctions.get(3).getFunction().apply(0, 20);
        Set<ArtifactoryPermission> expectedPermissions = onlyReadPermission ?
                Set.of(ArtifactoryPermission.READ) : Set.of(ArtifactoryPermission.values());
        verify(spyDescriptorFilter, times(1)).accept(any(RepoDescriptor.class), eq(expectedPermissions));
    }

    @Test
    public void testByKey_withInactiveFilter_showsVirtualRepos() {
        createMocks();
        when(repoService.getAllRepoKeysByFirstCharMapAndProject(DEFAULT_PROJECT))
                .thenReturn(Map.of('a', List.of("a-local", "a-proj-virt-1", "a-proj-virt-2")));
        stubRepo("a-local", new LocalRepoDescriptor());
        stubRepo("a-proj-virt-1", new VirtualRepoDescriptor());
        stubRepo("a-proj-virt-2", new VirtualRepoDescriptor());

        RootFetchByRepoKey strategy = new RootFetchByRepoKey(new TreeFilter());
        List<FetchFunction<RepositoryNode>> fetchFunctions = strategy.getFetchFunctions();

        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 3L);
        ContinueResult<RepositoryNode> result = fetchFunctions.get(3).getFunction().apply(0, 10);
        assertEquals(result.getData().size(), 3);
    }

    @Test
    public void testByKey_withActiveFilter_hidesProjectScopedVirtualRepos() {
        createMocks();
        // Override the character map: single 'a' group with 1 local and 2 project-scoped virtual repos
        when(repoService.getAllRepoKeysByFirstCharMapAndProject(DEFAULT_PROJECT))
                .thenReturn(Map.of('a', List.of("a-local", "a-proj-virt-1", "a-proj-virt-2")));
        stubRepo("a-local", new LocalRepoDescriptor());
        stubRepo("a-proj-virt-1", new VirtualRepoDescriptor());
        stubRepo("a-proj-virt-2", new VirtualRepoDescriptor());

        // Active filter: user is not a member of the owning project — hide all virtual repos
        when(projectsService.createVirtualReposVisibilityFilter(DEFAULT_PROJECT))
                .thenReturn(repo -> false);

        RootFetchByRepoKey strategy = new RootFetchByRepoKey(new TreeFilter());
        List<FetchFunction<RepositoryNode>> fetchFunctions = strategy.getFetchFunctions();

        // 3 super functions + 1 character group
        assertEquals(fetchFunctions.size(), 4);
        // FetchFunction count reflects the post-filter size (virtual repos are not counted)
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 1L);
        ContinueResult<RepositoryNode> result = fetchFunctions.get(3).getFunction().apply(0, 10);
        assertEquals(result.getData().size(), 1);
        assertEquals(result.getData().getFirst().getRepoKey(), "a-local");
    }

    @Test
    public void testByKey_withActiveFilter_paginationIsConsistentWhenVirtualRepoHiddenMidAlphabet() {
        createMocks();
        // 'a' group sorted: a-local-1, a-local-2, a-local-3, a-virt-1 (virt hidden mid-list)
        when(repoService.getAllRepoKeysByFirstCharMapAndProject(DEFAULT_PROJECT))
                .thenReturn(Map.of('a', List.of("a-local-1", "a-virt-1", "a-local-2", "a-local-3")));
        stubRepo("a-local-1", new LocalRepoDescriptor());
        stubRepo("a-local-2", new LocalRepoDescriptor());
        stubRepo("a-local-3", new LocalRepoDescriptor());
        stubRepo("a-virt-1", new VirtualRepoDescriptor());

        when(projectsService.createVirtualReposVisibilityFilter(DEFAULT_PROJECT))
                .thenReturn(repo -> false);

        RootFetchByRepoKey strategy = new RootFetchByRepoKey(new TreeFilter());
        FetchFunction<RepositoryNode> charGroupFn = strategy.getFetchFunctions().get(3);

        assertEquals(charGroupFn.getOriginalSize().longValue(), 3L);

        // Page 1: first 2 repos
        ContinueResult<RepositoryNode> page1 = charGroupFn.getFunction().apply(0, 2);
        assertEquals(page1.getData().size(), 2);
        assertEquals(page1.getData().get(0).getRepoKey(), "a-local-1");
        assertEquals(page1.getData().get(1).getRepoKey(), "a-local-2");

        // Page 2: continues exactly where page 1 ended — no overlap, no gap
        int continueIndex = Integer.parseInt(page1.getContinueState());
        ContinueResult<RepositoryNode> page2 = charGroupFn.getFunction().apply(continueIndex, 2);
        assertEquals(page2.getData().size(), 1);
        assertEquals(page2.getData().getFirst().getRepoKey(), "a-local-3");
    }

    @Test
    public void testByKey_withActiveFilter_mustIncludeIsFilteredVirtualRepo() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled))
                .thenReturn(true);

        when(repoService.getAllRepoKeysByFirstCharMapAndProject(DEFAULT_PROJECT))
                .thenReturn(Map.of('a', List.of("a-local-1", "a-local-2", "a-local-3", "a-virt-proj")));
        stubRepo("a-local-1", new LocalRepoDescriptor());
        stubRepo("a-local-2", new LocalRepoDescriptor());
        stubRepo("a-local-3", new LocalRepoDescriptor());
        stubRepo("a-virt-proj", new VirtualRepoDescriptor());

        // Active filter hides the virtual repo that mustInclude is pointing at
        when(projectsService.createVirtualReposVisibilityFilter(DEFAULT_PROJECT))
                .thenReturn(repo -> false);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("a-virt-proj");
        RootFetchByRepoKey strategy = new RootFetchByRepoKey(treeFilter);
        FetchFunction<RepositoryNode> charGroupFn = strategy.getFetchFunctions().get(3);

        // Count reflects only the 3 visible local repos
        assertEquals(charGroupFn.getOriginalSize().longValue(), 3L);

        // mustInclude target is not in the filtered list, so adjustLimit falls back to repos.size() (3).
        // All 3 visible repos are returned; the hidden virtual repo does NOT appear.
        ContinueResult<RepositoryNode> result = charGroupFn.getFunction().apply(0, 1);
        assertEquals(result.getData().size(), 3);
        assertTrue(result.getData().stream().noneMatch(n -> "a-virt-proj".equals(n.getRepoKey())),
                "Hidden virtual repo must not appear even when specified as mustInclude");
    }

    @DataProvider
    public Object[][] booleanProvider() {
        return new Object[][] {
                {true},
                {false}
        };
    }

    private void stubRepo(String key, RepoBaseDescriptor descriptor) {
        descriptor.setKey(key);
        descriptor.setType(RepoType.Generic);
        when(repoService.repoDescriptorByKey(key)).thenReturn(descriptor);
    }
}
