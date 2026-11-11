package org.artifactory.storage;

import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.binstore.BinariesInfo;
import org.jfrog.storage.binstore.ifc.model.BinariesDirInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryProvidersInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryTreeElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author annakv
 */
public class StorageSummaryImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    ArtifactoryContext artifactoryContext;
    AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        StorageService storageService = mock(StorageService.class);
        when(storageService.getFileStoreStorageSummary()).thenReturn(new FileStoreStorageSummary(List.of(
                new BinariesDirInfo(10, 11, "dirpath")),
                new BinaryProvidersInfo<>("cache", new BinaryTreeElement<>())));
        when(artifactoryContext.beanForType(StorageService.class)).thenReturn(storageService);
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @Test
    void storageSummaryImplTest() {
        Map<String, RepoStorageSummaryInfo > repoStorageSummaries = Map.of();
        BinariesInfo binariesInfo = new BinariesInfo(10, 11);
        Map<String, Long> projectsTotalSizes = Map.of();
        Map<String, Long> projectsTotalFilesCount = Map.of();
        StorageSummaryInfo storageSummaryInfo = new StorageSummaryInfo(repoStorageSummaries, binariesInfo,
                projectsTotalSizes, projectsTotalFilesCount);
        StorageSummaryImpl newStorageSummaryImpl = new StorageSummaryImpl(storageSummaryInfo);
        assertNotNull(newStorageSummaryImpl);
        assertEquals("dirpath", newStorageSummaryImpl.getFileStoreSummary().getStorageDirectory());
    }

}
