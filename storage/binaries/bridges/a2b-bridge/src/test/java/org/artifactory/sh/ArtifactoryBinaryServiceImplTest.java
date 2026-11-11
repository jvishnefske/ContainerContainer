package org.artifactory.sh;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.sh.service.ArtifactoryBinaryServiceImpl;
import org.jfrog.storage.binstore.InternalBinaryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class ArtifactoryBinaryServiceImplTest {
    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    private ArtifactoryBinaryServiceImpl artifactoryBinaryService;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = openMocks(this);
        ArtifactoryHome.bind(artifactoryHome);
        when(artifactorySystemProperties.getLongProperty(ConstantValues.rtfsStatusRefreshIntervalSecs)).thenReturn(60L);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        artifactoryBinaryService = new ArtifactoryBinaryServiceImpl();
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @Test
    void testDeleteRepoFailedTasksByBinary() {
        int expectedResult = 42;
        InternalBinaryService binaryService = mock(InternalBinaryService.class);
        artifactoryBinaryService.setBinaryService(binaryService);
        Mockito.when(binaryService.deleteRepoFailedTasksByBinary(anyString(), any())).thenReturn(expectedResult);
        int result = artifactoryBinaryService.deleteRepoFailedTasksByBinary("fds", "fd");
        assertEquals(result, expectedResult);
    }

    @Test
    void testDeleteAllTasksPerRepo() {
        int expectedResult = 42;
        InternalBinaryService binaryService = mock(InternalBinaryService.class);
        artifactoryBinaryService.setBinaryService(binaryService);
        Mockito.when(binaryService.deleteAllTasksPerRepo(any())).thenReturn(expectedResult);
        int result = artifactoryBinaryService.deleteAllTasksPerRepo("fds");
        assertEquals(result, expectedResult);
    }

    @ParameterizedTest(name = "[{index}] {arguments}")
    @CsvSource(useHeadersInDisplayName = true, textBlock =
            """
                    Storage as MS, IsDeleted
                    false, false
                    false, true
                    true,  false
                    true,  true
                    """
    )
    void testDeleteBinary(boolean expectedResult) {
        InternalBinaryService binaryService = mock(InternalBinaryService.class);
        artifactoryBinaryService.setBinaryService(binaryService);

        Mockito.when(binaryService.deleteBinary(any(String.class))).thenReturn(expectedResult);
        boolean result = artifactoryBinaryService.deleteBinary("sha1");
        assertEquals(result, expectedResult);
        Mockito.verify(binaryService).deleteBinary("sha1");
    }

}