package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.RootFetchStrategyTest;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.RepositoryNode;
import org.artifactory.ui.rest.model.continuous.dtos.ContinueTreeDto;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.commons.continues.ContinueResult;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author Omri Ziv
 */
@Test
public class RootNodeTest extends RootFetchStrategyTest {

    @Test
    public void testFetchItems() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        rootNode.setContinueTreeDto(continueTreeDto);
        continueTreeDto.setLimit(10L);
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(10, continueResult.getData().size());
        assertEquals("11", continueResult.getContinueState());
    }

    @Test
    public void testFetchItemsByProject() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        rootNode.setContinueTreeDto(continueTreeDto);
        continueTreeDto.setProjectKey(PROJECT_A);
        continueTreeDto.setLimit(7L);
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(7, continueResult.getData().size());
        assertEquals("8", continueResult.getContinueState());
    }

    @Test
    public void testFetchItemsMustInclude() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        rootNode.setContinueTreeDto(continueTreeDto);
        continueTreeDto.setLimit(10L);
        continueTreeDto.setMustInclude("Maven-virt-repo001");
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(30, continueResult.getData().size());
        assertEquals("32", continueResult.getContinueState());
    }

    @Test
    public void testFetchItemsMustIncludeByProject() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        rootNode.setContinueTreeDto(continueTreeDto);
        continueTreeDto.setProjectKey(PROJECT_A);
        continueTreeDto.setLimit(2L);
        continueTreeDto.setMustInclude("npm-repo003");
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(4, continueResult.getData().size());
        assertEquals("5", continueResult.getContinueState());
    }

    @Test(description = "RTFACT-20444")
    public void testFetchItemsMustIncludeVirtualOrderByRepoType() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        continueTreeDto.setLimit(3L);
        continueTreeDto.setMustInclude("Maven-virt-repo003");
        continueTreeDto.setRepositoryTypes(Collections.singletonList(RepositoryType.VIRTUAL));
        rootNode.setContinueTreeDto(continueTreeDto);
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(9, continueResult.getData().size());
        assertEquals("10", continueResult.getContinueState());
        List<String> keys = StreamSupportUtils.stream(continueResult.getData())
                .map(repoNode -> ((RepositoryNode) repoNode).getText())
                .collect(Collectors.toList());
        assertTrue(keys.contains("Maven-virt-repo003"));
    }

    @Test
    public void testFetchItemsMustIncludeNotFound() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        rootNode.setContinueTreeDto(continueTreeDto);
        continueTreeDto.setLimit(10L);
        continueTreeDto.setMustInclude("Maven-virt-repo091");
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(31, continueResult.getData().size());
        assertNull(continueResult.getContinueState());
    }

    @Test
    public void testFetchItemsMustIncludeNotFoundByProject() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        rootNode.setContinueTreeDto(continueTreeDto);
        continueTreeDto.setProjectKey(PROJECT_A);
        continueTreeDto.setLimit(2L);
        continueTreeDto.setMustInclude("npm-virt-repo091");
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(9L, continueResult.getData().size());
        assertNull(continueResult.getContinueState());
    }

    @Test
    public void testFetchItemsByPackageType() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        continueTreeDto.setLimit(300L);
        continueTreeDto.setSortBy(TreeFilter.SortBy.PACKAGE_TYPE);
        rootNode.setContinueTreeDto(continueTreeDto);
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(31, continueResult.getData().size());
        assertNull(continueResult.getContinueState());
        assertTrue(testRepositoryNodeBefore(continueResult.getData(), "Docker-virt-repo001", "Maven-repo001"));
    }

    @Test
    public void testFetchItemsAll() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        continueTreeDto.setLimit(300L);
        rootNode.setContinueTreeDto(continueTreeDto);
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(31, continueResult.getData().size());
        assertNull(continueResult.getContinueState());
        assertTrue(testRepositoryNodeBefore(continueResult.getData(), "Maven-repo001", "Docker-virt-repo001"));
        assertTrue(testRepositoryNodeBefore(continueResult.getData(), "Maven-repo001", "Maven-repo002"));
        assertTrue(testRepositoryNodeBefore(continueResult.getData(), "Maven-repo001", "Maven-virt-repo001"));
    }

    @Test
    public void testFetchItemsAllByProject() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        continueTreeDto.setLimit(300L);
        rootNode.setContinueTreeDto(continueTreeDto);
        continueTreeDto.setProjectKey(PROJECT_A);
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(9, continueResult.getData().size());
        assertNull(continueResult.getContinueState());
    }

    @Test
    public void testFetchItemsAllDefaultPageSize() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        rootNode.setContinueTreeDto(continueTreeDto);
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(31, continueResult.getData().size());
        assertNull(continueResult.getContinueState());
        assertTrue(testRepositoryNodeBefore(continueResult.getData(), "Maven-repo001", "Docker-virt-repo001"));
        assertTrue(testRepositoryNodeBefore(continueResult.getData(), "Maven-repo001", "Maven-repo002"));
        assertTrue(testRepositoryNodeBefore(continueResult.getData(), "Maven-repo001", "Maven-virt-repo001"));
    }

    @Test
    public void testFetchItemsFilteredByPackageType() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        continueTreeDto.setPackageTypes(Arrays.asList(RepoType.Conda, RepoType.Docker, RepoType.Maven));
        continueTreeDto.setLimit(300L);
        rootNode.setContinueTreeDto(continueTreeDto);
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(23, continueResult.getData().size());
        assertNull(continueResult.getContinueState());
        assertTrue(testRepositoryNodeBefore(continueResult.getData(), "Maven-repo001", "Docker-virt-repo001"));
        assertTrue(testRepositoryNodeBefore(continueResult.getData(), "Maven-repo001", "Maven-repo002"));
        assertTrue(testRepositoryNodeBefore(continueResult.getData(), "Maven-repo001", "Maven-virt-repo001"));
    }

    @Test
    public void testFetchItemsAllWithContinuationDefaultProject() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        continueTreeDto.setLimit(10L);
        rootNode.setContinueTreeDto(continueTreeDto);
        boolean readMore = true;
        String continueState = "0";
        ContinueResult<? extends RestModel> continueResult = null;
        List<RestModel> totalResults = new ArrayList<>();
        while (readMore) {
            continueTreeDto.setContinueState(continueState);
            continueResult = rootNode.fetchItemTypeData(true, false);
            continueState = continueResult.getContinueState();
            totalResults.addAll(continueResult.getData());
            if (continueState == null) {
                readMore = false;
            }
        }

        assertEquals(31, totalResults.size());
        assertNull(continueResult.getContinueState());
        assertTrue(testRepositoryNodeBefore(totalResults, "Maven-repo001", "Maven-repo002"));
        assertTrue(testRepositoryNodeBefore(totalResults, "Maven-repo001", "Maven-repo002"));
        assertTrue(testRepositoryNodeBefore(totalResults, "Maven-repo001", "Maven-virt-repo001"));
    }

    @Test
    public void testFetchItemsAllWithContinuationProjectContext() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        continueTreeDto.setProjectKey(PROJECT_A);
        continueTreeDto.setLimit(2L);
        rootNode.setContinueTreeDto(continueTreeDto);
        boolean readMore = true;
        String continueState = "0";
        ContinueResult<? extends RestModel> continueResult = null;
        List<RestModel> totalResults = new ArrayList<>();
        while (readMore) {
            continueTreeDto.setContinueState(continueState);
            continueResult = rootNode.fetchItemTypeData(true, false);
            continueState = continueResult.getContinueState();
            totalResults.addAll(continueResult.getData());
            if (continueState == null) {
                readMore = false;
            }
        }

        assertEquals(9, totalResults.size());
        assertNull(continueResult.getContinueState());
    }

    @Test
    public void testFetchItemsWithFavorites() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        rootNode.setContinueTreeDto(continueTreeDto);
        continueTreeDto.setLimit(10L);
        continueTreeDto.setRepositoryKeys(Arrays.asList("Maven-virt-repo002", "npm-repo003"));
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(3, continueResult.getData().size());
        assertTrue(testRepositoryNodeBefore(continueResult.getData(), "npm-repo003", "Maven-virt-repo002"));
        assertNull(continueResult.getContinueState());
    }

    @Test
    public void testFetchItemsWithFavoritesByProject() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        rootNode.setContinueTreeDto(continueTreeDto);
        continueTreeDto.setLimit(10L);
        continueTreeDto.setRepositoryKeys(Arrays.asList("npm-repo003", "npm-repo005"));
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(3, continueResult.getData().size());
        assertTrue(testRepositoryNodeBefore(continueResult.getData(), "npm-repo003", "npm-repo005"));
        assertNull(continueResult.getContinueState());
    }

    @Test
    public void testFetchItemsWithFavoritesByPackageType() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        continueTreeDto.setLimit(10L);
        continueTreeDto.setSortBy(TreeFilter.SortBy.PACKAGE_TYPE);
        continueTreeDto.setRepositoryKeys(Arrays.asList("Maven-virt-repo002", "Docker-repo001"));
        rootNode.setContinueTreeDto(continueTreeDto);
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(3, continueResult.getData().size());
        assertTrue(testRepositoryNodeBefore(continueResult.getData(), "Docker-repo001", "Maven-virt-repo002"));
        assertNull(continueResult.getContinueState());
    }

    @Test
    public void testFetchItemsWithFavoritesOnlyVirtual() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        continueTreeDto.setLimit(10L);
        continueTreeDto.setRepositoryTypes(Collections.singletonList(RepositoryType.VIRTUAL));
        continueTreeDto.setRepositoryKeys(Arrays.asList("Maven-virt-repo002", "Docker-repo001"));
        rootNode.setContinueTreeDto(continueTreeDto);
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(2, continueResult.getData().size());
        assertEquals(((RepositoryNode) continueResult.getData().get(1)).getText(), "Maven-virt-repo002");
        assertNull(continueResult.getContinueState());
    }

    @Test
    public void testFetchItemsWithFavoritesNotExisted() {
        createMocksWithLocalAndVirtual();
        RootNode rootNode = new RootNode();
        ContinueTreeDto continueTreeDto = new ContinueTreeDto();
        continueTreeDto.setLimit(10L);
        continueTreeDto.setSortBy(TreeFilter.SortBy.PACKAGE_TYPE);
        continueTreeDto.setRepositoryKeys(Arrays.asList("not-existed-001", "not-existed-002", "not-existed-003"));
        rootNode.setContinueTreeDto(continueTreeDto);
        ContinueResult<? extends RestModel> continueResult = rootNode.fetchItemTypeData(true, false);
        assertEquals(1, continueResult.getData().size()); // Only trash
        assertNull(continueResult.getContinueState());
    }
}