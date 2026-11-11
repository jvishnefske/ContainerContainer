package org.artifactory.rest.common.util;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Andrey Tashlitsky
 */
class RestUtilsTest {

    private static final String BASE_URL = "http://cool.com/cool";
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private CentralConfigDescriptor descriptor;
    @Mock
    private HttpServletRequest request;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getUrlBase()).thenReturn(BASE_URL);
        when(request.getScheme()).thenReturn("http");
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @ParameterizedTest
    @MethodSource("relativePaths")
    void testBuildStorageInfoUri(String repoKey, String relativePath, String expectedUri) {
        String uri = RestUtils.buildStorageInfoUri(request, repoKey, relativePath);
        assertEquals(expectedUri, uri);
    }

    private static Stream<Arguments> relativePaths() {
        return Stream.of(
                Arguments.of("cool-repo", "cool-path", BASE_URL + "/api/storage/cool-repo/cool-path"),
                Arguments.of("cooler-repo", "", BASE_URL + "/api/storage/cooler-repo"),
                Arguments.of("coolest-repo", null, BASE_URL + "/api/storage/coolest-repo")
        );
    }

    @Test
    void testBuildDownloadUri() {
        String uri = RestUtils.buildDownloadUri(request, "cool-repo", "cool-path");
        assertEquals(BASE_URL + "/cool-repo/cool-path", uri);
    }

    @Test
    void testBuildSecurityInfoUri() {
        String uri = RestUtils.buildSecurityInfoUri(request, "cool-type", "cool-key");
        assertEquals(BASE_URL + "/api/security/cool-type/cool-key", uri);
    }
}
