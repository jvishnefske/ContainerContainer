package org.artifactory.sorter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.aql.result.Row;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author Shahar Palmor
 */
class SortedFileTreeTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private ArtifactoryContext context;
    @Mock
    private ObjectMapper objectMapper;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.getArtifactoryHome()).thenReturn(getOrCreateArtifactoryHomeStub());

    }

    @AfterEach
    public void finish() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testAddSortedFileTree() throws IOException {
        SortedFileTree sortedFileTree = spy(new SortedFileTree());
        sortedFileTree.addSortedFileTree(objectMapper, new TreeSet<Row>());
        Assertions.assertEquals(sortedFileTree.getFiles().size(), 1);
    }

    @Test
    public void testAddSortedFileTreeFails() {
        SortedFileTree sortedFileTree = spy(new SortedFileTree());
        try {
            doThrow(IOException.class).when(objectMapper).writeValue((File) any(),any());
            sortedFileTree.addSortedFileTree(objectMapper, new TreeSet<Row>());
            fail("Should have thrown an exception");
        } catch (IOException e) {
            assertTrue(e instanceof IOException);
        }
    }
}