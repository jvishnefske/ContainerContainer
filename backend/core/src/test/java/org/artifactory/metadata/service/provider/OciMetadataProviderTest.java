package org.artifactory.metadata.service.provider;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class OciMetadataProviderTest {

    AutoCloseable closeable;
    OciMetadataProvider ociMetadataProvider;

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        ociMetadataProvider = spy(new OciMetadataProvider());
    }

    @AfterEach
    void cleanup() throws Exception {
        closeable.close();
    }

    @Test
    void getRepoType() {
        Assertions.assertEquals(RepoType.OCI, ociMetadataProvider.getRepoType());
    }

    @Test
    void pkgIdResolverRepoNamePropNull() {
        MutableMetadataEntityBOM mutableMetadataEntityBOM = Mockito.mock(MutableMetadataEntityBOM.class);
        Properties properties = Mockito.mock(Properties.class);
        when(properties.getFirst("docker.repoName")).thenReturn(null);
        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        MetadataResolver metadataResolver = ociMetadataProvider.pkgIdResolver();
        metadataResolver.apply(mutableMetadataEntityBOM, properties, fileInfo, null);
        verifyNoInteractions(mutableMetadataEntityBOM);
    }

    @Test
    void pkgIdResolver() {
        MutableMetadataEntityBOM mutableMetadataEntityBOM = Mockito.mock(MutableMetadataEntityBOM.class);
        Properties properties = Mockito.mock(Properties.class);
        when(properties.getFirst("docker.repoName")).thenReturn("repo");
        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        MetadataResolver metadataResolver = ociMetadataProvider.pkgIdResolver();
        metadataResolver.apply(mutableMetadataEntityBOM, properties, fileInfo, null);
        verify(mutableMetadataEntityBOM, times(1)).setPkgid(ociMetadataProvider.trimToSize("docker://repo",
                255));
        verifyNoMoreInteractions(mutableMetadataEntityBOM);
    }
}