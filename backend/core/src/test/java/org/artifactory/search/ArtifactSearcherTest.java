package org.artifactory.search;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.search.artifact.ArtifactSearchControls;
import org.artifactory.sapi.search.VfsComparatorType;
import org.artifactory.sapi.search.VfsQuery;
import org.artifactory.sapi.search.VfsQueryResult;
import org.artifactory.sapi.search.VfsQueryResultType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class ArtifactSearcherTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private VfsQuery vfsQuery;

    private ArtifactSearcher artifactSearcher;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        artifactSearcher = spy(new ArtifactSearcher());
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testDoSearch() {
        ArtifactSearchControls controls = new ArtifactSearchControls();

        doReturn(vfsQuery).when(artifactSearcher).createQuery(controls);
        when(vfsQuery.expectedResult(VfsQueryResultType.FILE)).thenReturn(vfsQuery);
        when(vfsQuery.prop("node_name")).thenReturn(vfsQuery);
        when(vfsQuery.caseInsensitive(true)).thenReturn(vfsQuery);
        when(vfsQuery.val((String) any())).thenReturn(vfsQuery);
        when(vfsQuery.prop("node_path")).thenReturn(vfsQuery);
        when(vfsQuery.comp(VfsComparatorType.NOT_CONTAINS)).thenReturn(vfsQuery);
        when(vfsQuery.val((String) any())).thenReturn(vfsQuery);
        VfsQueryResult vfsQueryResult = mock(VfsQueryResult.class);
        controls.setQuery("queryWithSpecialChars%");
        when(vfsQuery.execute(1000)).thenReturn(vfsQueryResult);
        when(vfsQuery.escapeSymbol(anyString())).thenReturn(vfsQuery);
        artifactSearcher.doSearch(controls);
        verify(vfsQuery, times(1)).execute(anyLong());
    }
}