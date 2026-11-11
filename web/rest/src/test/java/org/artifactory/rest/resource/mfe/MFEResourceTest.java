package org.artifactory.rest.resource.mfe;

import org.artifactory.api.web.FeatureFlagService;
import org.artifactory.rest.resource.mfe.MfeName;
import org.artifactory.rest.resource.mfe.MfeProjectConfig;
import org.artifactory.rest.resource.mfe.MfeProjectRegistry;
import org.artifactory.rest.resource.mfe.server.MFEFileServer;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.artifactory.exception.http.NotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MFEResourceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private MfeProjectRegistry mfeProjectRegistry;

    @BeforeEach
    void setUp() {
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .build();
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .build();
        lenient().when(mfeProjectRegistry.findProjectByMfeName(MfeName.CORE)).thenReturn(coreProject);
        lenient().when(mfeProjectRegistry.findProjectByMfeName(MfeName.RTUI)).thenReturn(rtuiProject);
        lenient().when(mfeProjectRegistry.findProjectByMfeName(null)).thenReturn(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"core", "rtui"})
    void getMfeVersion_shouldReturnVersionResponse_whenGivenValidMfeName(String mfeName) throws Exception {
        MFEFileServer mfeFileServer = Mockito.mock(MFEFileServer.class);
        Mockito.when(mfeFileServer.getMfeVersion(mfeName)).thenReturn("1.0.0");
        FeatureFlagService featureFlagService = Mockito.mock(FeatureFlagService.class);
        Mockito.when(featureFlagService.featureFlagsHash()).thenReturn("ffHash");
        MFEResource resource = new MFEResource(mfeFileServer, featureFlagService, mfeProjectRegistry);

        Object entity = resource.getMfeVersion(mfeName).getEntity();
        assertInstanceOf(VersionResponse.class, entity);
        VersionResponse versionResponse = (VersionResponse) entity;
        assertEquals("1.0.0", versionResponse.version());
        assertEquals("ffHash", versionResponse.featureFlagsHash());
    }

    @Test
    void getMfeVersion_shouldReturnNotFound_whenGivenUnknownMfeName() {
        MFEFileServer mfeFileServer = Mockito.mock(MFEFileServer.class);
        FeatureFlagService featureFlagService = Mockito.mock(FeatureFlagService.class);
        MFEResource resource = new MFEResource(mfeFileServer, featureFlagService, mfeProjectRegistry);

        Object entity = resource.getMfeVersion("unknown").getEntity();
        assertEquals("Could not find provided mfe name: unknown", entity);
    }

    @Test
    void getMfeVersion_shouldReturnNotFound_whenGetMfeVersionThrowsNotFoundException() throws Exception {
        MFEFileServer mfeFileServer = Mockito.mock(MFEFileServer.class);
        Mockito.when(mfeFileServer.getMfeVersion("core")).thenThrow(new NotFoundException("Version not found"));
        FeatureFlagService featureFlagService = Mockito.mock(FeatureFlagService.class);
        MFEResource resource = new MFEResource(mfeFileServer, featureFlagService, mfeProjectRegistry);

        jakarta.ws.rs.core.Response response = resource.getMfeVersion("core");

        assertEquals(jakarta.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertEquals("Version not found", response.getEntity());
    }

}