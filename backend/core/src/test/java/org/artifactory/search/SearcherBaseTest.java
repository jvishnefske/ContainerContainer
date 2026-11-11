package org.artifactory.search;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.search.ItemSearchResults;
import org.artifactory.api.search.SearchControlsBase;
import org.artifactory.api.search.SearchResultBase;
import org.artifactory.aql.AqlException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SearcherBaseTest extends ArtifactoryHomeBoundJUnitTest {
    @BeforeEach
    void setUp() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void count() {
        assertEquals(0, new SearcherBaseDefaultImpl().count(null));
    }

    @Test
    void doCount() {
        assertEquals(0, new SearcherBaseDefaultImpl().doCount(null));
    }

    @Test
    void testSearchWithAqlException() {
        SearcherBaseDefaultImpl searcher = new SearcherBaseDefaultImpl() {
            @Override
            public ItemSearchResults<SearchResultBase> doSearch(SearchControlsBase controls) {
                throw new AqlException("Request timeout: execution timed out");
            }
        };
        
        final AqlException exception =
                assertThrows(AqlException.class, () -> searcher.search(null));
        
        assertEquals("Request timeout: execution timed out", exception.getMessage());
    }

    static class SearcherBaseDefaultImpl extends SearcherBase<SearchControlsBase, SearchResultBase> {
        @Override
        public ItemSearchResults<SearchResultBase> doSearch(SearchControlsBase controls) {
            return null;
        }
    }
}