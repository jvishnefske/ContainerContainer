package org.artifactory.search;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.rest.search.common.RestDateFieldName;
import org.artifactory.api.search.ItemSearchResults;
import org.artifactory.api.search.artifact.ArtifactSearchResult;
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.fs.ItemInfo;
import org.artifactory.sapi.search.VfsQuery;
import org.artifactory.sapi.search.VfsQueryResult;
import org.artifactory.sapi.search.VfsQueryResultType;
import org.artifactory.sapi.search.VfsQueryService;
import org.artifactory.search.stats.LastDownloadedItemsSearcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Spy
    private SearchServiceImpl searchService;
    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;

    AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        ArtifactoryHome.bind(artifactoryHome);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @Test
    void testGetAqlItemsListNotDownloadedSince() {
        prepareSearchService();
        Calendar calendar = Calendar.getInstance();
        List<AqlItem> aqlItems = searchService.
                getAqlItemsListNotDownloadedSince(calendar, calendar, "repo");
        assertEquals(2, aqlItems.size());
    }

    @Test
    void testStreamArtifactsItemInfoNotDownloadedSince() {
        prepareSearchService();
        Calendar calendar = Calendar.getInstance();
        Stream<ItemInfo> itemInfoStream = searchService.
                streamArtifactsItemInfoNotDownloadedSince(calendar, calendar, 100, false,"repo");
        assertEquals(2, itemInfoStream.count());
    }

    @Test
    void testSearchArtifactsInRange() {
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getLongProperty(ConstantValues.limitSearchResultsInDatesRange))
                .thenReturn(1L);
        Calendar from = Calendar.getInstance();
        Calendar to = Calendar.getInstance();
        VfsQuery queryBuilder = Mockito.mock(VfsQuery.class, Mockito.RETURNS_SELF);
        VfsQueryService vfsQueryService = Mockito.mock(VfsQueryService.class);
        when(vfsQueryService.createQuery()).thenReturn(queryBuilder);
        when(queryBuilder.expectedResult(VfsQueryResultType.FILE)).thenReturn(queryBuilder);
        when(queryBuilder.setRepoKeys(any())).thenReturn(queryBuilder);
        when(queryBuilder.name(any())).thenReturn(queryBuilder);
        when(queryBuilder.comp(any())).thenReturn(queryBuilder);
        VfsQueryResult vfsQueryResult = Mockito.mock(VfsQueryResult.class);
        when(vfsQueryResult.getCount()).thenReturn(1234L);
        when(queryBuilder.execute(any(Long.class))).thenReturn(vfsQueryResult);
        ReflectionTestUtils.setField(searchService, "vfsQueryService", vfsQueryService);

        ItemSearchResults<ArtifactSearchResult> searchResults = searchService.searchArtifactsInRange(from, to,
                    List.of("repo"), RestDateFieldName.CREATED);

        assertEquals(1234L, searchResults.getFullResultsCount());
    }

    private void prepareSearchService() {
        VisibleAqlItemsSearchHelper searcher = Mockito.mock(VisibleAqlItemsSearchHelper.class);
        LastDownloadedItemsSearcher lastDownloadedItemsSearcher = new LastDownloadedItemsSearcher(searcher);
        ReflectionTestUtils.setField(searchService, "lastDownloadedItemsSearcher", lastDownloadedItemsSearcher);
        AqlItem item = new AqlBaseFullRowImpl(Map.of());
        when(searcher.searchVisibleArtifactsAsStream(any(), any(), anyBoolean(), any()))
                .thenReturn(Stream.of(item, item));
    }
}
