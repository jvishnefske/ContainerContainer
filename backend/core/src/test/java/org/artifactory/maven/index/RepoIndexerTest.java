package org.artifactory.maven.index;

import org.apache.lucene.store.FSDirectory;
import org.apache.maven.index.context.IndexingContext;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.fs.FileInfo;
import org.artifactory.mime.MavenNaming;
import org.artifactory.mime.MimeTypes;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.storage.fs.service.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.artifactory.mime.MimeType.def;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepoIndexerTest {

    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private FileService fileService;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        MimeTypes mimeTypes = mock(MimeTypes.class);
        when(mimeTypes.getByExtension(any())).thenReturn(def);
        when(artifactoryHome.getMimeTypes()).thenReturn(mimeTypes);
        when(context.beanForType(FileService.class)).thenReturn(fileService);
        when(context.getArtifactoryHome()).thenReturn(artifactoryHome);
        ArtifactoryHome.bind(artifactoryHome);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @Test
    void testMergeIntoNoLeftovers() throws Exception {
        StoringRepo virtualRepo = mock(StoringRepo.class);
        RepoIndexer repoIndexer = new RepoIndexer(virtualRepo);
        StoringRepo localRepo = mock(StoringRepo.class);
        RepoPath nexusIndexFile = RepoPathFactory.create("l", MavenNaming.NEXUS_INDEX_GZ_PATH);
        when(localRepo.getRepoPath(MavenNaming.NEXUS_INDEX_GZ_PATH)).thenReturn(
                nexusIndexFile);
        FileResource fileResource = new FileResource(new FileInfoImpl(nexusIndexFile));
        when(localRepo.getInfo(any())).thenReturn(fileResource);
        Map<StoringRepo, FSDirectory> extractedRepoIndexes = new HashMap<>();
        ResourceStreamHandle resourceStreamHandle = mock(ResourceStreamHandle.class);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 0});
        when(resourceStreamHandle.getInputStream()).thenReturn(inputStream);
        when(localRepo.getResourceStreamHandle(any(), any())).thenReturn(resourceStreamHandle);

        File tempDirectory = Files.createTempDirectory(getClass().getName()).toFile();
        when(artifactoryHome.getTempWorkDir()).thenReturn(tempDirectory);
        try {
            IndexingContext indexingContext = mock(IndexingContext.class);
            ReflectionTestUtils.setField(repoIndexer, "context", indexingContext);
            repoIndexer.mergeInto(localRepo, extractedRepoIndexes);
            assertEquals(0, Objects.requireNonNull(tempDirectory.list()).length);
        } finally {
            boolean delete = tempDirectory.delete();
            if (!delete) {
                tempDirectory.deleteOnExit();
            }
        }

    }

    @Test
    void testCreateContext() throws IOException {
        DbLocalRepo repo = mock(DbLocalRepo.class);
        when(repo.getKey()).thenReturn("some");
        RepoPath rootPath = RepoPathFactory.create("some");
        FileInfo rootInfo = mock(FileInfo.class);
        when(rootInfo.getRepoPath()).thenReturn(rootPath);
        when(fileService.loadItem(rootPath)).thenReturn(rootInfo);
        when(repo.getRepoPath("")).thenReturn(rootPath);
        RepoIndexer repoIndexer = new RepoIndexer(repo);
        File dir = Files.createTempDirectory("mvnIndexer").toFile();

        assertDoesNotThrow(() -> repoIndexer.createContext(dir));

        verify(fileService, times(1)).loadItem(rootPath);
    }
}