package org.artifactory.storage.service;

import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author annakv
 */
public class BinaryInfoServiceImplTest {

    @Test
    public void testGetStorageSize() {
        ArtifactoryBinaryService artifactoryBinaryService = mock(ArtifactoryBinaryService.class);
        when(artifactoryBinaryService.getStorageSize()).thenReturn(2L);
        BinaryInfoServiceImpl service = new BinaryInfoServiceImpl(artifactoryBinaryService);
        long info = service.getStorageSize();
        assertEquals(info, 2);
        verify(artifactoryBinaryService, times(1)).getStorageSize();
    }

}
