package org.artifactory.metadata.service.provider;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.artifactory.metadata.service.provider.OciMetadataProvider.MANIFEST_TYPE_PROP;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class HelmOciMetadataProviderTest {
    AutoCloseable closeable;
    HelmOciMetadataProvider helmOciMetadataProvider;

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        helmOciMetadataProvider = spy(new HelmOciMetadataProvider());
    }

    @AfterEach
    void cleanup() throws Exception {
        closeable.close();
    }

    @Test
    void getTypeShouldReturnHelm() {
        Properties properties = Mockito.mock(Properties.class);
        when(properties.getFirst(MANIFEST_TYPE_PROP)).thenReturn("application/vnd.oci.image.");
        Assertions.assertEquals(RepoType.Helm.getType(), helmOciMetadataProvider.getType(properties));
    }

    @Test
    void getTypeShouldHelm_propsNull() {
        Properties properties = Mockito.mock(Properties.class);
        when(properties.getFirst(MANIFEST_TYPE_PROP)).thenReturn(null);
        Assertions.assertEquals(RepoType.Helm.getType(), helmOciMetadataProvider.getType(properties));
    }

    @Test
    void getRepoType() {
        Assertions.assertEquals(RepoType.HelmOCI, helmOciMetadataProvider.getRepoType());
    }
}