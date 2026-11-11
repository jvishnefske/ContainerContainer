package org.artifactory.rest.resource.release;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.bundle.BundleType;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.ws.rs.core.Response;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * @author Yevdo Abramov
 * Created on 13/06/2024
 */
public class ReleaseBundleResourceTest {

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ReleaseBundleAddon releaseBundleAddon;

    private AutoCloseable mocks;
    private ReleaseBundleResource releaseBundleResource;

    @BeforeMethod
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        releaseBundleResource = new ReleaseBundleResource(addonsManager);
        when(addonsManager.addonByType(ReleaseBundleAddon.class)).thenReturn(releaseBundleAddon);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
        verifyNoMoreInteractions(addonsManager, releaseBundleAddon);
    }

    @Test
    public void testGetSourceReleaseBundleChecksum() {
        when(releaseBundleAddon.getBundleJson("bundleName", "version", BundleType.SOURCE))
                .thenReturn(null);
        Response response = releaseBundleResource.getSourceReleaseBundleChecksum("bundleName", "version");
        assertNull(response.getHeaderString(ArtifactoryHeaders.CHECKSUM_SHA256));
        assertEquals(response.getStatus(), 404);

        verify(addonsManager).addonByType(ReleaseBundleAddon.class);
        verify(releaseBundleAddon).getBundleJson("bundleName", "version", BundleType.SOURCE);
        verifyNoMoreInteractions(addonsManager, releaseBundleAddon);
        Mockito.clearInvocations(addonsManager, releaseBundleAddon);


        when(releaseBundleAddon.getBundleJson("bundleName", "version", BundleType.SOURCE))
                .thenReturn("checksum");
        response = releaseBundleResource.getSourceReleaseBundleChecksum("bundleName", "version");
        assertEquals(response.getHeaderString(ArtifactoryHeaders.CHECKSUM_SHA256),
                "96fa8f226d3801741e807533552bc4b177ac4544d834073b6a5298934d34b40b");
        assertEquals(response.getStatus(), 200);

        verify(addonsManager).addonByType(ReleaseBundleAddon.class);
        verify(releaseBundleAddon).getBundleJson("bundleName", "version", BundleType.SOURCE);
    }
}