package org.artifactory.descriptor.repo;

import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalCacheRepoDescriptorTest extends ArtifactoryHomeBoundJUnitTest {

    RemoteRepoDescriptor remoteRepoDescriptor;

    @BeforeEach
    void setUp() {
        remoteRepoDescriptor = spy(RemoteRepoDescriptor.class);
    }

    @Test
    void testCdnConfig() {
        LocalCacheRepoDescriptor localCacheRepoDescriptor = new LocalCacheRepoDescriptor();
        localCacheRepoDescriptor.setRemoteRepo(remoteRepoDescriptor);

        when(remoteRepoDescriptor.isCdnRedirect()).thenReturn(false);
        assertFalse(localCacheRepoDescriptor.isCdnRedirect());

        when(remoteRepoDescriptor.isCdnRedirect()).thenReturn(true);
        assertTrue(localCacheRepoDescriptor.isCdnRedirect());
    }

    @Test
    void testCdnConfigSet() {
        LocalCacheRepoDescriptor localCacheRepoDescriptor = new LocalCacheRepoDescriptor();
        localCacheRepoDescriptor.setRemoteRepo(remoteRepoDescriptor);

        localCacheRepoDescriptor.setCdnRedirect(true);
        verify(remoteRepoDescriptor).setCdnRedirect(true);

        localCacheRepoDescriptor.isCdnRedirect();
        verify(remoteRepoDescriptor).isCdnRedirect();
    }

    @Test
    void testSetXrayNotAvailableMode() {
        LocalCacheRepoDescriptor localCacheRepoDescriptor = new LocalCacheRepoDescriptor();
        localCacheRepoDescriptor.setRemoteRepo(remoteRepoDescriptor);

        localCacheRepoDescriptor.setXrayNotAvailableMode(XrayNotAvailableMode.FAIL_CLOSE);
        verify(remoteRepoDescriptor).setXrayNotAvailableMode(XrayNotAvailableMode.FAIL_CLOSE);

        localCacheRepoDescriptor.getXrayNotAvailableMode();
        verify(remoteRepoDescriptor).getXrayNotAvailableMode();
    }
}
