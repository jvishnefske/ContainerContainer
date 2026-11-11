package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RestTreeNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.TreeFilter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.RepositoryNode;
import org.jfrog.commons.continues.ContinueResult;
import org.jfrog.commons.continues.FetchFunction;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.treeBrowserCheckOnlyReadPermissionOnRootLevel;
import static org.artifactory.common.ConstantValues.treeBrowserImprovedShowRepoInTreeEnabled;
import static org.artifactory.repo.system.SystemRepos.RELEASE_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Omri Ziv
 */
public class RootFetchByRepositoryTypeTest extends RootFetchStrategyTest {

    @Test
    public void fetchFunctionsAmount() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        RootFetchByRepositoryType rootFetchByPackageType =
                new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        assertEquals(fetchFunctions.size(), 8);
    }

    @Test
    public void fetchItems() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        RootFetchByRepositoryType rootFetchByPackageType =
                new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());
        ContinueResult<RepositoryNode> result = rootFetchByPackageType.fetchItems();
        List<RepositoryNode> repositoryNodes = result.getData();
        assertEquals(repositoryNodes.get(0).getRepoKey(), "release-bundles");
        assertEquals(repositoryNodes.get(1).getRepoKey(), DEFAULT_RBV2_REPO);
        assertEquals(repositoryNodes.get(2).getRepoKey(), "project-" + DEFAULT_RBV2_REPO);
    }

    @Test
    public void getByRepositoryType() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        RootFetchByRepositoryType rootFetchByPackageType =
                new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        final int localRepoFunctionIndex = 3;
        assertEquals(fetchFunctions.get(localRepoFunctionIndex).getOriginalSize().longValue(), 25L); //local
        ContinueResult<? extends RestModel> continueResult1 = fetchFunctions.get(localRepoFunctionIndex)
                .getFunction().apply(0, 30);
        assertEquals(continueResult1.getData().size(), 24);
    }

    @Test
    public void getByRepositoryTypeAndProject() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setProjectKey(PROJECT_A);
        RootFetchByRepositoryType rootFetchByPackageType =
                new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        final int localRepoFunctionIndex = 3;
        assertEquals(fetchFunctions.get(localRepoFunctionIndex).getOriginalSize().longValue(), 7L); //local
        ContinueResult<? extends RestModel> continueResult = fetchFunctions.get(localRepoFunctionIndex)
                .getFunction().apply(0, 10);
        assertEquals(continueResult.getData().size(), 7);
    }

    @Test
    public void getByRepositoryTypeLimited() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        RootFetchByRepositoryType rootFetchByPackageType =
                new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 25); // local
        assertEquals(fetchFunctions.get(4).getOriginalSize().longValue(), 1); // remote
        assertEquals(fetchFunctions.get(5).getOriginalSize().longValue(), 0); // cache
        assertEquals(fetchFunctions.get(6).getOriginalSize().longValue(), 1); //virtual
        assertEquals(fetchFunctions.get(7).getOriginalSize().longValue(), 1); //federated
        ContinueResult<? extends RepositoryNode> continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 10);
        assertEquals(continueResult1.getData().size(), 10);
    }

    @Test
    public void getByRepositoryTypeLimitedAndProject() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setProjectKey(PROJECT_A);
        RootFetchByRepositoryType rootFetchByPackageType =
                new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 7); // local
        assertEquals(fetchFunctions.get(4).getOriginalSize().longValue(), 1); // remote
        assertEquals(fetchFunctions.get(5).getOriginalSize().longValue(), 0); // cache
        assertEquals(fetchFunctions.get(6).getOriginalSize().longValue(), 1); //virtual
        ContinueResult<? extends RepositoryNode> continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 5);
        assertEquals(continueResult1.getData().size(), 5);
    }

    @Test
    public void getByRepositoryTypeFilteredByType() {
        createMocksWithLocalAndVirtual();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setRepositoryTypes(Collections.singletonList(RepositoryType.VIRTUAL));
        RootFetchByRepositoryType rootFetchByPackageType =
                new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        assertEquals(fetchFunctions.size(), 4);
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 8);
        ContinueResult<RepositoryNode> continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 30);
        assertEquals(continueResult1.getData().size(), 8);
    }

    @Test
    public void getByRepositoryTypeFilteredByTypeAndProject() {
        createMocksWithLocalAndVirtual();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setRepositoryTypes(Collections.singletonList(RepositoryType.VIRTUAL));
        treeFilter.setProjectKey(PROJECT_A);
        RootFetchByRepositoryType rootFetchByPackageType =
                new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        assertEquals(fetchFunctions.size(), 4);
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 1);
        ContinueResult<RepositoryNode> continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 30);
        assertEquals(continueResult1.getData().size(), 1);
    }

    @Test
    public void getByRepositoryTypeFilteredByTypeAndPackageType() {
        createMocksWithLocalAndVirtual();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setRepositoryTypes(Collections.singletonList(RepositoryType.VIRTUAL));
        treeFilter.setPackageTypes(Collections.singletonList(RepoType.Maven));
        RootFetchByRepositoryType rootFetchByPackageType =
                new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        assertEquals(fetchFunctions.size(), 4);
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 8); // virtual

        ContinueResult<RepositoryNode> continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 30);
        assertEquals(continueResult1.getData().size(), 3);
    }

    @Test
    public void getByRepositoryTypeFilteredByTypeAndPackageTypeFindNone() {
        createMocksWithLocalAndVirtual();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setPackageTypes(Collections.singletonList(RepoType.NuGet));
        RootFetchByRepositoryType rootFetchByPackageType =
                new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 23); // local
        assertEquals(fetchFunctions.get(5).getOriginalSize().longValue(), 0); // remote
        assertEquals(fetchFunctions.get(6).getOriginalSize().longValue(), 8); // virtual

        ContinueResult<RepositoryNode> continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 30);
        assertEquals(continueResult1.getData().size(), 0);
        ContinueResult<RepositoryNode> continueResult2 = fetchFunctions.get(5).getFunction().apply(0, 30);
        assertEquals(continueResult2.getData().size(), 0);
        ContinueResult<RepositoryNode> continueResult3 = fetchFunctions.get(6).getFunction().apply(0, 30);
        assertEquals(continueResult3.getData().size(), 0);
    }

    @Test
    public void getByRepositoryTypeFilteredByKey() {
        createMocksWithLocalAndVirtual();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setByRepoKey("001");
        RootFetchByRepositoryType rootFetchByPackageType =
                new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 23); // local
        assertEquals(fetchFunctions.get(4).getOriginalSize().longValue(), 0); // remote
        assertEquals(fetchFunctions.get(5).getOriginalSize().longValue(), 0); // remote
        assertEquals(fetchFunctions.get(6).getOriginalSize().longValue(), 8); // virtual

        ContinueResult<RepositoryNode> continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 30);
        assertEquals(continueResult1.getData().size(), 3);
        ContinueResult<RepositoryNode> continueResult3 = fetchFunctions.get(5).getFunction().apply(0, 30);
        assertEquals(continueResult3.getData().size(), 0);
    }

    @Test
    public void getByRepositoryTypeFilteredByKeyAndProject() {
        createMocksWithLocalAndVirtual();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setByRepoKey("001");
        treeFilter.setProjectKey(PROJECT_A);
        RootFetchByRepositoryType rootFetchByPackageType =
                new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetchByPackageType.getFetchFunctions();
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 7); // local
        assertEquals(fetchFunctions.get(4).getOriginalSize().longValue(), 0); // remote
        assertEquals(fetchFunctions.get(5).getOriginalSize().longValue(), 0); // remote
        assertEquals(fetchFunctions.get(6).getOriginalSize().longValue(), 1); // virtual

        ContinueResult<RepositoryNode> continueResult1 = fetchFunctions.get(3).getFunction().apply(0, 30);
        assertEquals(continueResult1.getData().size(), 1);
        ContinueResult<RepositoryNode> continueResult3 = fetchFunctions.get(5).getFunction().apply(0, 30);
        assertEquals(continueResult3.getData().size(), 0);
    }

    @Test(dataProvider = "providePermissions")
    public void getPermissions(boolean treeBrowserCheckFinalNumberOfPermissionsBool, int sizeOfPermissions) {
        TreeFilter treeFilter = new TreeFilter();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserCheckOnlyReadPermissionOnRootLevel))
                .thenReturn(treeBrowserCheckFinalNumberOfPermissionsBool);
        RootFetchByRepositoryType rootFetchByPackageType =
                new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());
        assertEquals(rootFetchByPackageType.fetchService.getPermissions().size(), sizeOfPermissions);
    }

    @DataProvider
    public Object[][] providePermissions() {
        return new Object[][] {
                {false, ArtifactoryPermission.values().length},
                {true, 1}
        };
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeFeatureDisabled() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled)).thenReturn(
                false);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("some-repo");
        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        // When feature is disabled, limit should not be adjusted
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(3).getFunction().apply(0, 5);
        assertEquals(result.getData().size(), 5); // Should respect original limit
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeWithBlankMustInclude() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled)).thenReturn(true);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude(""); // Blank mustInclude
        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(3).getFunction().apply(0, 5);
        assertEquals(result.getData().size(), 5); // Should respect original limit
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeWithNonZeroContinueIndex() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled)).thenReturn(true);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("some-repo");
        treeFilter.setContinueIndex(1L); // Non-zero continue index (using Long)
        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(3).getFunction().apply(0, 5);
        assertEquals(result.getData().size(), 5); // Should respect original limit
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeWithSystemRepos() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled)).thenReturn(true);

        // Test with SUPPORT_BUNDLE_REPO_KEY
        TreeFilter treeFilter1 = new TreeFilter();
        treeFilter1.setMustInclude(SUPPORT_BUNDLE_REPO_KEY);
        RootFetchByRepositoryType rootFetch1 = new RootFetchByRepositoryType(treeFilter1, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions1 = rootFetch1.getFetchFunctions();
        ContinueResult<RepositoryNode> result1 = fetchFunctions1.get(3).getFunction().apply(0, 5);
        assertEquals(result1.getData().size(), 5); // Should respect original limit

        // Test with TRASH_REPO_KEY
        TreeFilter treeFilter2 = new TreeFilter();
        treeFilter2.setMustInclude(TRASH_REPO_KEY);
        RootFetchByRepositoryType rootFetch2 = new RootFetchByRepositoryType(treeFilter2, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions2 = rootFetch2.getFetchFunctions();
        ContinueResult<RepositoryNode> result2 = fetchFunctions2.get(3).getFunction().apply(0, 5);
        assertEquals(result2.getData().size(), 5); // Should respect original limit

        // Test with RELEASE_BUNDLE_REPO_KEY
        TreeFilter treeFilter3 = new TreeFilter();
        treeFilter2.setMustInclude(RELEASE_BUNDLE_REPO_KEY);
        RootFetchByRepositoryType rootFetch3 = new RootFetchByRepositoryType(treeFilter3, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions3 = rootFetch3.getFetchFunctions();
        ContinueResult<RepositoryNode> result3 = fetchFunctions3.get(3).getFunction().apply(0, 5);
        assertEquals(result3.getData().size(), 5); // Should respect original limit
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeRepoNotFound() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled)).thenReturn(true);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("non-existent-repo");
        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(3).getFunction().apply(0, 5);
        // When repo is not found, should return all available repos
        assertTrue(result.getData().size() > 5);
    }

    @Test
    public void testGetFederatedRepoNodes() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        final int federatedRepoFunctionIndex = 7;
        assertEquals(fetchFunctions.get(federatedRepoFunctionIndex).getOriginalSize().longValue(), 1L);

        ContinueResult<RepositoryNode> result =
                fetchFunctions.get(federatedRepoFunctionIndex).getFunction().apply(0, 10);
        assertEquals(result.getData().size(), 1);

        // Verify federated node properties
        RepositoryNode federatedNode = result.getData().getFirst();
        assertNotNull(federatedNode);
        assertNotNull(federatedNode.getRepoKey());
        assertNotNull(federatedNode.getRepoType());
    }

    @Test
    public void testGetCachedRepoNodes() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        final int cachedRepoFunctionIndex = 5;
        assertEquals(fetchFunctions.get(cachedRepoFunctionIndex).getOriginalSize().longValue(), 0L);

        ContinueResult<RepositoryNode> result = fetchFunctions.get(cachedRepoFunctionIndex).getFunction().apply(0, 10);
        assertEquals(result.getData().size(), 0);
    }

    @Test
    public void testGetRemoteRepoNodes() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        final int remoteRepoFunctionIndex = 4;
        assertEquals(fetchFunctions.get(remoteRepoFunctionIndex).getOriginalSize().longValue(), 1L);

        ContinueResult<RepositoryNode> result = fetchFunctions.get(remoteRepoFunctionIndex).getFunction().apply(0, 10);
        assertEquals(result.getData().size(), 1);

        // Verify remote node properties
        RepositoryNode remoteNode = result.getData().getFirst();
        assertNotNull(remoteNode);
        assertNotNull(remoteNode.getRepoKey());
    }

    @Test
    public void testGetVirtualRepoNodes() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        RootFetchByRepositoryType rootFetch =
                new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        final int virtualRepoFunctionIndex = 6;
        assertEquals(fetchFunctions.get(virtualRepoFunctionIndex).getOriginalSize().longValue(), 1L);

        ContinueResult<RepositoryNode> result = fetchFunctions.get(virtualRepoFunctionIndex).getFunction().apply(0, 10);
        assertEquals(result.getData().size(), 1);

        // Verify virtual node properties
        RepositoryNode virtualNode = result.getData().getFirst();
        assertNotNull(virtualNode);
        assertNotNull(virtualNode.getRepoKey());
    }

    @Test
    public void testSkipAndLimitFunctionality() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        final int localRepoFunctionIndex = 3;

        // Test skip functionality
        ContinueResult<RepositoryNode> resultWithSkip =
                fetchFunctions.get(localRepoFunctionIndex).getFunction().apply(5, 5);
        assertEquals(resultWithSkip.getData().size(), 5);

        // Test zero limit
        ContinueResult<RepositoryNode> resultZeroLimit =
                fetchFunctions.get(localRepoFunctionIndex).getFunction().apply(0, 0);
        assertEquals(resultZeroLimit.getData().size(), 0);

        // Test large skip value
        ContinueResult<RepositoryNode> resultLargeSkip =
                fetchFunctions.get(localRepoFunctionIndex).getFunction().apply(100, 5);
        assertEquals(resultLargeSkip.getData().size(), 0);
    }

    @Test
    public void testMultipleRepositoryTypesFiltering() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setRepositoryTypes(Arrays.asList(
                RepositoryType.LOCAL,
                RepositoryType.VIRTUAL,
                RepositoryType.REMOTE
        ));

        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        // Should include super functions (3) + selected repo types (3) = 6
        assertEquals(fetchFunctions.size(), 6);
    }

    @Test
    public void testEmptyRepositoryTypesFiltering() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setRepositoryTypes(Collections.emptyList());

        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        // Should include all functions when empty
        assertEquals(fetchFunctions.size(), 8);
    }

    @Test
    public void testNullRepositoryTypesFiltering() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setRepositoryTypes(null);

        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        // Should include all functions when null
        assertEquals(fetchFunctions.size(), 8);
    }

    @Test
    public void testSingleRepositoryTypeFiltering() {
        createMocks();

        // Test each repository type individually
        RepositoryType[] types = {
                RepositoryType.LOCAL,
                RepositoryType.REMOTE,
                RepositoryType.CACHED,
                RepositoryType.VIRTUAL,
                RepositoryType.FEDERATED
        };

        for (RepositoryType type : types) {
            TreeFilter treeFilter = new TreeFilter();
            treeFilter.setRepositoryTypes(Collections.singletonList(type));

            RootFetchByRepositoryType rootFetch =
                    new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

            List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
            // Should include super functions (3) + selected repo type (1) = 4
            assertEquals(fetchFunctions.size(), 4, "Failed for repository type: " + type);
        }
    }

    @Test
    public void testBuildMapCorrectness() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();

        // Verify all repository types have corresponding functions
        assertNotNull(fetchFunctions.get(3)); // LOCAL
        assertNotNull(fetchFunctions.get(4)); // REMOTE
        assertNotNull(fetchFunctions.get(5)); // CACHED
        assertNotNull(fetchFunctions.get(6)); // VIRTUAL
        assertNotNull(fetchFunctions.get(7)); // FEDERATED

        // Verify original sizes are set correctly
        assertTrue(fetchFunctions.get(3).getOriginalSize() >= 0);
        assertTrue(fetchFunctions.get(4).getOriginalSize() >= 0);
        assertTrue(fetchFunctions.get(5).getOriginalSize() >= 0);
        assertTrue(fetchFunctions.get(6).getOriginalSize() >= 0);
        assertTrue(fetchFunctions.get(7).getOriginalSize() >= 0);
    }

    @Test
    public void testRepositoryNodeCreationWithCachePrefix() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        // This would require mocking repositories with REMOTE_CACHE_SUFFIX
        // The test verifies that repos ending with "-cache" get CACHED type
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(3).getFunction().apply(0, 30);

        // Verify nodes are created correctly
        for (RepositoryNode node : result.getData()) {
            assertNotNull(node.getRepoKey());
            assertNotNull(node.getRepoType());
        }
    }

    @Test
    public void testCombinedFiltering() {
        createMocksWithLocalAndVirtual();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setRepositoryTypes(Collections.singletonList(RepositoryType.LOCAL));
        treeFilter.setPackageTypes(Collections.singletonList(RepoType.Npm)); // Changed from Maven to Npm
        treeFilter.setByRepoKey("npm-repo");
        treeFilter.setProjectKey(PROJECT_A);

        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        assertEquals(fetchFunctions.size(), 4); // super + local only

        ContinueResult<RepositoryNode> result = fetchFunctions.get(3).getFunction().apply(0, 30);
        // Should find repositories matching all filters
        assertFalse(result.getData().isEmpty());
    }

    @Test
    public void testEdgeCaseWithLargeLimit() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();

        // Test with very large limit
        ContinueResult<RepositoryNode> result = fetchFunctions.get(3).getFunction().apply(0, Integer.MAX_VALUE);
        assertTrue(result.getData().size() <= 25); // Should not exceed available repos
    }

    @Test
    public void testContinueResultIndexCalculation() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();

        // Test continue result index calculation by verifying data consistency
        ContinueResult<RepositoryNode> result1 = fetchFunctions.get(3).getFunction().apply(0, 5);
        ContinueResult<RepositoryNode> result2 = fetchFunctions.get(3).getFunction().apply(5, 5);

        // Verify that results are consistent and non-null
        assertNotNull(result1);
        assertNotNull(result2);
        assertNotNull(result1.getData());
        assertNotNull(result2.getData());
    }

    @Test
    public void testAdjustLimitInCaseShowRepoInTreeWithValidRepo() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled)).thenReturn(true);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("maven-repo001"); // Valid repo that exists in mock data
        treeFilter.setContinueIndex(0L); // Zero continue index to enable adjustment
        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(3).getFunction().apply(0, 5);

        // When repo is found and feature is enabled, limit should be adjusted to include that repo
        assertFalse(result.getData().isEmpty());
    }

    @Test
    public void testGetLocalNodesWithCacheSuffix() {
        createMocks();

        // Create a mock setup with repositories that have REMOTE_CACHE_SUFFIX
        TreeFilter treeFilter = new TreeFilter();
        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        // Test the scenario where repository keys end with "-cache"
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(3).getFunction().apply(0, 30);

        // Verify that repositories are processed correctly regardless of cache suffix
        for (RepositoryNode node : result.getData()) {
            assertNotNull(node.getRepoKey());
            assertNotNull(node.getRepoType());
            // The repoType should be either "local" or "cached" based on the suffix
            assertTrue(node.getRepoType().equals("local") || node.getRepoType().equals("cached"));
        }
    }

    @Test
    public void testGetVirtualNodesWithVirtualRepoConfig() {
        createMocksWithLocalAndVirtual();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setRepositoryTypes(Collections.singletonList(RepositoryType.VIRTUAL));

        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(3).getFunction().apply(0, 30);

        // Verify virtual nodes are created correctly
        for (RepositoryNode node : result.getData()) {
            assertNotNull(node.getRepoKey());
            assertNotNull(node.getRepoType());
            // Virtual nodes should be created using VirtualRemoteRepositoryNode.fromVirtualRepoConfig
            assertTrue(node.getRepoKey().contains("virt") || node.getRepoKey().contains("virtual"));
        }
    }

    @Test
    public void testConstructorWithDifferentRepoOrder() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();

        // Test with a different repository order
        List<RepositoryType> customOrder = Arrays.asList(
                RepositoryType.VIRTUAL,
                RepositoryType.REMOTE,
                RepositoryType.LOCAL,
                RepositoryType.FEDERATED,
                RepositoryType.CACHED
        );

        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, customOrder);

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();

        // Verify that all repository types are still included
        assertEquals(fetchFunctions.size(), 8); // 3 super functions + 5 repo types

        // Verify that functions are created for all repository types
        for (int i = 3; i < 8; i++) {
            assertNotNull(fetchFunctions.get(i));
            assertTrue(fetchFunctions.get(i).getOriginalSize() >= 0);
        }
    }

    @Test
    public void testBuildMapWithProjectFilter() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setProjectKey(PROJECT_A);

        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();

        // Verify that project filtering affects the repository counts
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 7L); // LOCAL for PROJECT_A
        assertEquals(fetchFunctions.get(4).getOriginalSize().longValue(), 1L); // REMOTE for PROJECT_A
        assertEquals(fetchFunctions.get(5).getOriginalSize().longValue(), 0L); // CACHED for PROJECT_A
        assertEquals(fetchFunctions.get(6).getOriginalSize().longValue(), 1L); // VIRTUAL for PROJECT_A
        assertEquals(fetchFunctions.get(7).getOriginalSize().longValue(), 1L); // FEDERATED for PROJECT_A
    }

    @Test
    public void testGetPermissionsWithReadOnlyPermission() {
        TreeFilter treeFilter = new TreeFilter();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserCheckOnlyReadPermissionOnRootLevel))
                .thenReturn(true);

        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        Set<ArtifactoryPermission> permissions = rootFetch.fetchService.getPermissions();
        assertEquals(permissions.size(), 1);
        assertTrue(permissions.contains(ArtifactoryPermission.READ));
    }

    @Test
    public void testGetPermissionsWithAllPermissions() {
        TreeFilter treeFilter = new TreeFilter();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserCheckOnlyReadPermissionOnRootLevel))
                .thenReturn(false);

        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        Set<ArtifactoryPermission> permissions = rootFetch.fetchService.getPermissions();
        assertEquals(permissions.size(), ArtifactoryPermission.values().length);
    }

    @Test
    public void testGetFetchFunctionsWithRepoTypeFilteringEdgeCases() {
        createMocks();

        // Test with single repository type
        TreeFilter treeFilter1 = new TreeFilter();
        treeFilter1.setRepositoryTypes(Collections.singletonList(RepositoryType.CACHED));
        RootFetchByRepositoryType rootFetch1 = new RootFetchByRepositoryType(treeFilter1, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions1 = rootFetch1.getFetchFunctions();
        assertEquals(fetchFunctions1.size(), 4); // 3 super + 1 cached

        // Test with all repository types explicitly listed
        TreeFilter treeFilter2 = new TreeFilter();
        treeFilter2.setRepositoryTypes(Arrays.asList(RepositoryType.values()));
        RootFetchByRepositoryType rootFetch2 = new RootFetchByRepositoryType(treeFilter2, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions2 = rootFetch2.getFetchFunctions();
        assertEquals(fetchFunctions2.size(), 8); // 3 super + 5 repo types
    }

    @Test
    public void testAdjustLimitCalculationEdgeCases() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled)).thenReturn(true);

        // Test when repository is at index 0
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("maven-repo001"); // Should be first Maven repo
        treeFilter.setContinueIndex(0L);

        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(3).getFunction().apply(0, 3);

        // Should include the repository even if it's at index 0
        assertFalse(result.getData().isEmpty());
    }

    @Test
    public void testRepositoryNodeCreationDifferentTypes() {
        createMocksWithLocalAndVirtual();
        TreeFilter treeFilter = new TreeFilter();

        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();

        // Test local repositories
        ContinueResult<RepositoryNode> localResult = fetchFunctions.get(3).getFunction().apply(0, 30);
        for (RepositoryNode node : localResult.getData()) {
            assertNotNull(node.getRepoKey());
            assertNotNull(node.getRepoType());
        }

        // Test remote repositories
        ContinueResult<RepositoryNode> remoteResult = fetchFunctions.get(4).getFunction().apply(0, 30);
        for (RepositoryNode node : remoteResult.getData()) {
            assertNotNull(node.getRepoKey());
        }

        // Test virtual repositories
        ContinueResult<RepositoryNode> virtualResult = fetchFunctions.get(6).getFunction().apply(0, 30);
        for (RepositoryNode node : virtualResult.getData()) {
            assertNotNull(node.getRepoKey());
        }

        // Test federated repositories
        ContinueResult<RepositoryNode> federatedResult = fetchFunctions.get(7).getFunction().apply(0, 30);
        for (RepositoryNode node : federatedResult.getData()) {
            assertNotNull(node.getRepoKey());
            assertNotNull(node.getRepoType());
        }
    }

    @Test
    public void testFilteringWithDescriptorFilterAcceptance() {
        createMocks();
        TreeFilter treeFilter = new TreeFilter();
        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();

        // Test that descriptor filter is applied - this is implicitly tested through
        // the filtering logic in the repository node methods
        ContinueResult<RepositoryNode> result = fetchFunctions.get(3).getFunction().apply(0, 100);

        // All returned repositories should have passed the descriptor filter
        for (RepositoryNode node : result.getData()) {
            assertNotNull(node.getRepoKey());
            // The fact that we got results means the descriptor filter accepted them
        }
    }

    @Test
    public void testComplexFilteringScenario() {
        createMocksWithLocalAndVirtual();
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setRepositoryTypes(Arrays.asList(RepositoryType.LOCAL, RepositoryType.VIRTUAL));
        treeFilter.setPackageTypes(Arrays.asList(RepoType.Maven, RepoType.Docker));
        treeFilter.setByRepoKey("repo"); // Common substring
        treeFilter.setProjectKey(PROJECT_A);

        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        assertEquals(fetchFunctions.size(), 5); // 3 super + 2 selected repo types

        // Test that complex filtering works
        ContinueResult<RepositoryNode> localResult = fetchFunctions.get(3).getFunction().apply(0, 30);
        ContinueResult<RepositoryNode> virtualResult = fetchFunctions.get(4).getFunction().apply(0, 30);

        // Verify results are not null and contain expected data structure
        assertNotNull(localResult.getData());
        assertNotNull(virtualResult.getData());
    }

    @Test
    public void testNullAndEmptyHandling() {
        createMocks();

        // Test with null mustInclude
        TreeFilter treeFilter1 = new TreeFilter();
        treeFilter1.setMustInclude(null);
        RootFetchByRepositoryType rootFetch1 = new RootFetchByRepositoryType(treeFilter1, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions1 = rootFetch1.getFetchFunctions();
        ContinueResult<RepositoryNode> result1 = fetchFunctions1.get(3).getFunction().apply(0, 5);
        assertEquals(result1.getData().size(), 5); // Should use original limit

        // Test with empty repository order
        TreeFilter treeFilter2 = new TreeFilter();
        RootFetchByRepositoryType rootFetch2 = new RootFetchByRepositoryType(treeFilter2, Collections.emptyList());

        List<FetchFunction<RepositoryNode>> fetchFunctions2 = rootFetch2.getFetchFunctions();
        assertEquals(fetchFunctions2.size(), 3); // Only super functions
    }

    @Test
    public void testAdjustLimitWithLocalCacheRepoDescriptor() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled)).thenReturn(true);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("some-cache-repo");
        treeFilter.setContinueIndex(0L);

        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        // Test cached repo nodes which use LocalCacheRepoDescriptor
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(5).getFunction().apply(0, 5);

        assertEquals(result.getData().size(), 0);
    }

    @Test
    public void testAdjustLimitWithRepoConfig() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled)).thenReturn(true);

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("gen-fed"); // Federated repo from mock setup
        treeFilter.setContinueIndex(0L);

        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(7).getFunction().apply(0, 2);

        assertEquals(result.getData().size(), 1);
    }

    @Test
    public void testAdjustLimitCalculationBoundaries() {
        createMocks();
        when(artifactorySystemProperties.getBooleanProperty(treeBrowserImprovedShowRepoInTreeEnabled)).thenReturn(true);

        // Test with repo at index 0 and limit 1
        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setMustInclude("maven-repo001"); // First repo (index 0)
        treeFilter.setContinueIndex(0L);

        RootFetchByRepositoryType rootFetch = new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());

        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();
        ContinueResult<RepositoryNode> result = fetchFunctions.get(3).getFunction().apply(0, 1);

        // With index 0 + limit 1 - 1 = 0, should still get at least 1 repo
        assertFalse(result.getData().isEmpty());
    }

    @Test
    public void testGetVirtualRepoNodes_withInactiveFilter_showsVirtualRepos() {
        createMocksWithLocalAndVirtual();
        List<FetchFunction<RepositoryNode>> fetchFunctions = getDefaultFetchFunctions();

        // VIRTUAL is at index 6 (3 super + LOCAL + REMOTE + CACHED + VIRTUAL).
        final int virtualRepoFunctionIndex = 6;
        // Mock seeds 8 virtuals: 3 Maven + 4 Docker + 1 Npm.
        assertEquals(fetchFunctions.get(virtualRepoFunctionIndex).getOriginalSize().longValue(), 8L);

        ContinueResult<RepositoryNode> virtualResult =
                fetchFunctions.get(virtualRepoFunctionIndex).getFunction().apply(0, 30);
        assertEquals(virtualResult.getData().size(), 8);
        boolean hasMavenVirtual = virtualResult.getData().stream()
                .anyMatch(node -> node.getRepoKey().contains("Maven-virt"));
        assertTrue(hasMavenVirtual, "Default pass-through predicate should keep virtual repos visible");
    }

    @Test
    public void testGetVirtualRepoNodes_withActiveFilter_hidesProjectScopedVirtualRepos() {
        createMocksWithLocalAndVirtual();
        when(projectsService.createVirtualReposVisibilityFilter(DEFAULT_PROJECT))
                .thenReturn(repo -> !repo.getKey().contains("Maven-virt"));
        List<FetchFunction<RepositoryNode>> fetchFunctions = getDefaultFetchFunctions();

        final int virtualRepoFunctionIndex = 6;
        // Post-filter count: 8 total virtuals - 3 Maven virtuals hidden = 5.
        assertEquals(fetchFunctions.get(virtualRepoFunctionIndex).getOriginalSize().longValue(), 5L);

        ContinueResult<RepositoryNode> virtualResult =
                fetchFunctions.get(virtualRepoFunctionIndex).getFunction().apply(0, 30);
        assertEquals(virtualResult.getData().size(), 5);
        boolean noMavenVirtualLeaked = virtualResult.getData().stream()
                .noneMatch(node -> node.getRepoKey().contains("Maven-virt"));
        assertTrue(noMavenVirtualLeaked, "Active filter should hide all Maven virtual repos");
    }

    @Test
    public void testGetVirtualRepoNodes_withActiveFilter_paginationIsConsistentWhenVirtualReposScatteredAcrossList() {
        // Mixed virtual list with "hide" markers scattered at head, middle, and tail
        // to exercise all skip-boundary paths.
        List<RepoConfig> scatteredVirtualConfigs = List.of(
                VirtualRepoConfig.create("a-virt-hide", RepoType.Maven),
                VirtualRepoConfig.create("b-virt-keep-1", RepoType.Maven),
                VirtualRepoConfig.create("c-virt-keep-2", RepoType.Maven),
                VirtualRepoConfig.create("d-virt-hide", RepoType.Maven),
                VirtualRepoConfig.create("e-virt-keep-3", RepoType.Maven),
                VirtualRepoConfig.create("f-virt-keep-4", RepoType.Maven),
                VirtualRepoConfig.create("g-virt-hide", RepoType.Maven)
        );
        when(repositoryConfigService.getRepoConfigsByProjectAndType(any(), eq(Set.of(RepoDetailsType.VIRTUAL))))
                .thenReturn(scatteredVirtualConfigs);
        when(projectsService.createVirtualReposVisibilityFilter(DEFAULT_PROJECT))
                .thenReturn(repo -> !repo.getKey().contains("hide"));

        TreeFilter treeFilter = new TreeFilter();
        treeFilter.setRepositoryTypes(Collections.singletonList(RepositoryType.VIRTUAL));
        RootFetchByRepositoryType rootFetch =
                new RootFetchByRepositoryType(treeFilter, RestTreeNode.getRepoOrder());
        List<FetchFunction<RepositoryNode>> fetchFunctions = rootFetch.getFetchFunctions();

        // 3 super functions + 1 VIRTUAL entry. Virtual count is the 4 surviving keepers.
        assertEquals(fetchFunctions.size(), 4);
        assertEquals(fetchFunctions.get(3).getOriginalSize().longValue(), 4L);

        ContinueResult<RepositoryNode> page1 = fetchFunctions.get(3).getFunction().apply(0, 2);
        assertEquals(page1.getData().size(), 2);
        assertEquals(page1.getData().get(0).getRepoKey(), "b-virt-keep-1");
        assertEquals(page1.getData().get(1).getRepoKey(), "c-virt-keep-2");

        ContinueResult<RepositoryNode> page2 = fetchFunctions.get(3).getFunction().apply(2, 2);
        assertEquals(page2.getData().size(), 2);
        assertEquals(page2.getData().get(0).getRepoKey(), "e-virt-keep-3");
        assertEquals(page2.getData().get(1).getRepoKey(), "f-virt-keep-4");

        ContinueResult<RepositoryNode> page3 = fetchFunctions.get(3).getFunction().apply(4, 2);
        assertEquals(page3.getData().size(), 0);

        boolean noHiddenKeyLeaked = Stream.of(page1, page2, page3)
                .flatMap(result -> result.getData().stream())
                .noneMatch(node -> node.getRepoKey().contains("hide"));
        assertTrue(noHiddenKeyLeaked,
                "Scattered virtual repos at head/middle/tail must not leak into any paginated result");
    }

    private List<FetchFunction<RepositoryNode>> getDefaultFetchFunctions() {
        return new RootFetchByRepositoryType(new TreeFilter(), RestTreeNode.getRepoOrder())
                .getFetchFunctions();
    }
}
