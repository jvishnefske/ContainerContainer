package org.artifactory.webapp.servlet.authentication;

import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.authentication.ArtifactoryAuthenticationType;
import org.artifactory.signature.SignedUrlService;
import org.artifactory.webapp.servlet.RequestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Stream;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArtifactorySignedUrlAuthenticationFilterTest {
    @Mock
    private SignedUrlService signedUrlService;
    @Mock
    private HttpServletRequest servletRequest;
    @Mock
    private HttpServletResponse servletResponse;

    @Test
    void getType() {
        ArtifactorySignedUrlAuthenticationFilter artifactorySignedUrlAuthenticationFilter =
                new ArtifactorySignedUrlAuthenticationFilter();
        Assertions.assertEquals(ArtifactoryAuthenticationType.ARTIFACTORY_SIGNED_URL,
                artifactorySignedUrlAuthenticationFilter.getType());
    }

    @ParameterizedTest
    @MethodSource("paths")
    void testDoFilter(String servletPath, String pathInfo, boolean isRepoRequest, String expectedPath)
            throws ServletException, IOException {
        ArtifactorySignedUrlAuthenticationFilter filter = new ArtifactorySignedUrlAuthenticationFilter();
        filter.signedUrlService = signedUrlService;
        String token = "token";

        when(servletRequest.getServletPath()).thenReturn(servletPath);
        when(servletRequest.getPathInfo()).thenReturn(pathInfo);
        when(servletRequest.getParameter(SystemRestConstants.PATH_PARAM_SIGNATURE)).thenReturn(token);

        try (MockedStatic<RequestUtils> requestUtilsMock = mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.isRepoRequest(servletRequest, false)).thenReturn(isRepoRequest);
            requestUtilsMock.when(() -> RequestUtils.getServletPathFromRequest(servletRequest))
                    .thenReturn(servletPath + (pathInfo == null ? "" : pathInfo));

            filter.doFilter(servletRequest, servletResponse, null);
            verify(signedUrlService, times(1)).verifySignedToken(expectedPath, token);
        }
    }

    private static Stream<Arguments> paths() {
        return Stream.of(
                Arguments.of("servletPath", null, true, "servletPath"),
                Arguments.of("servletPath", "/pathInfo", true, "servletPath"),
                Arguments.of("servletPath", "/pathInfo", false, "servletPath/pathInfo")
        );
    }
}