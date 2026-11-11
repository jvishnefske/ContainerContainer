package org.artifactory.search.deployable;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.search.ItemSearchResults;
import org.artifactory.api.search.deployable.VersionUnitSearchControls;
import org.artifactory.api.search.deployable.VersionUnitSearchResult;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.search.VfsQueryService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionUnitSearcherTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext context;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private VfsQueryService vfsQueryService;
    private VersionUnitSearcher versionUnitSearcher;
    private AutoCloseable autoCloseable;

    @BeforeEach
    public void beforeMethod() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        getBound().bind();
        when(context.beanForType(InternalRepositoryService.class)).thenReturn(repositoryService);
        when(context.beanForType(VfsQueryService.class)).thenReturn(vfsQueryService);
        versionUnitSearcher = new VersionUnitSearcher();
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        unbindArtifactoryHome();
        autoCloseable.close();
    }

    @Test
    public void testGetAqlQueryForSearch_WithEmptyPath_UsesWildcard() {
        // Given
        RepoPath emptyPathToSearch = mock(RepoPath.class);
        when(emptyPathToSearch.getPath()).thenReturn("");
        when(emptyPathToSearch.getRepoKey()).thenReturn("test-repo");

        // When
        AqlApiItem result = versionUnitSearcher.getAqlQueryForSearch(emptyPathToSearch, true);

        // Then
        AqlApiItem expectedAql = AqlApiItem.create().filter(
                AqlApiItem.and(
                        AqlApiItem.repo().equal("test-repo"),
                        AqlApiItem.path().matches("*")
                )
        );

        assertEquals(expectedAql.toNative(0), result.toNative(0));
    }

    @Test
    public void testGetAqlQueryForSearch_WithExactMatch() {
        // Given
        RepoPath exactPathToSearch = mock(RepoPath.class);
        String expectedPath = "some/path";
        when(exactPathToSearch.getPath()).thenReturn(expectedPath);
        when(exactPathToSearch.getRepoKey()).thenReturn("test-repo");

        // When
        AqlApiItem result = versionUnitSearcher.getAqlQueryForSearch(exactPathToSearch, true);

        // Then
        AqlApiItem expectedAql = AqlApiItem.create().filter(
                AqlApiItem.and(
                        AqlApiItem.repo().equal("test-repo"),
                        AqlApiItem.path().equal(expectedPath)
                )
        );
        assertEquals(expectedAql.toNative(0), result.toNative(0));
    }

    @Test
    public void testGetAqlQueryForSearch_WithNonExactMatch() {
        // Given
        RepoPath nonExactPathToSearch = mock(RepoPath.class);
        String expectedPath = "some/path";
        when(nonExactPathToSearch.getPath()).thenReturn(expectedPath);
        when(nonExactPathToSearch.getRepoKey()).thenReturn("test-repo");

        // When
        AqlApiItem result = versionUnitSearcher.getAqlQueryForSearch(nonExactPathToSearch, false);

        // Then
        AqlApiItem expectedAql = AqlApiItem.create().filter(
                AqlApiItem.and(
                        AqlApiItem.repo().equal("test-repo"),
                        AqlApiItem.or(
                                AqlApiItem.path().matches(expectedPath + "/*"),
                                AqlApiItem.path().equal(expectedPath)
                        )
                )
        );

        assertEquals(expectedAql.toNative(0), result.toNative(0));
    }

    @Test
    public void testDoSearch_WhenRepoDoesNotExist_ReturnsEmptyResults() {
        // Given
        RepoPath searchPath = mock(RepoPath.class);
        when(searchPath.getRepoKey()).thenReturn("non-existent-repo");

        VersionUnitSearchControls controls = new VersionUnitSearchControls(searchPath);

        when(repositoryService.repositoryByKey("non-existent-repo")).thenReturn(null);

        // When
        ItemSearchResults<VersionUnitSearchResult> results = versionUnitSearcher.doSearch(controls);

        // Then
        assertEquals(0, results.getResults().size());
        assertEquals(Collections.emptyList(), results.getResults());
    }
}