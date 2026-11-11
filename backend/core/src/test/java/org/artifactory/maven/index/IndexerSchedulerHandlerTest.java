package org.artifactory.maven.index;

import org.artifactory.descriptor.index.IndexerDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndexerSchedulerHandlerTest {

    private IndexerDescriptor newDescriptor;
    private IndexerDescriptor oldDescriptor;
    private MavenIndexerServiceImpl.IndexerSchedulerHandler handler;

    @BeforeEach
    public void setUp() {
        newDescriptor = Mockito.mock(IndexerDescriptor.class);
        oldDescriptor = Mockito.mock(IndexerDescriptor.class);
        handler = new MavenIndexerServiceImpl.IndexerSchedulerHandler(newDescriptor, oldDescriptor);
    }

    @Test
    void testJobName() {
        assertEquals("Indexer", handler.jobName());
    }

    @Test
    void testGetNewDescriptors() {
        List<IndexerDescriptor> newDescriptors = handler.getNewDescriptors();
        assertEquals(1, newDescriptors.size());
        assertEquals(newDescriptor, newDescriptors.get(0));
    }

    @Test
    void testGetOldDescriptors() {
        List<IndexerDescriptor> oldDescriptors = handler.getOldDescriptors();
        assertEquals(1, oldDescriptors.size());
        assertEquals(oldDescriptor, oldDescriptors.get(0));
    }

    @Test
    void testFindOldFromNew() {
        IndexerDescriptor result = handler.findOldFromNew(newDescriptor);
        assertEquals(oldDescriptor, result);
    }
}