package org.artifactory.util;

import org.apache.http.HttpHeaders;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.webapp.servlet.util.ServletUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.HttpMethod;
import java.util.Set;
import java.util.stream.Stream;

import static jakarta.servlet.http.HttpServletResponse.SC_MOVED_TEMPORARILY;
import static org.artifactory.webapp.servlet.util.ServletUtils.redirectToNewNativeBrowser;
import static org.artifactory.webapp.servlet.util.ServletUtils.shouldRedirectToUiNativeBrowserRelativeToAuthChain;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.assertEquals;

/**
 * @author Uriah Levy
 */
public class ServletUtilsTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ArtifactoryContext context;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private RepositoryService repositoryService;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = openMocks(this);
        Mockito.when(context.getCentralConfig()).thenReturn(centralConfigService);
        Mockito.when(context.getRepositoryService()).thenReturn(repositoryService);
        bindArtifactoryHome();
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        unbindArtifactoryHome();
        mocks.close();
    }

    @ParameterizedTest
    @MethodSource("provideUserAgent")
    public void testShouldRedirectToNewNativeBrowser(String userAgent, String requestUri, boolean beforeAuthChain,
            boolean shouldRedirect) {
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET, "artifactory/" + requestUri);
        request.addHeader(HttpHeaders.USER_AGENT, userAgent);
        request.setContextPath("artifactory");
        request.setScheme("http");
        Mockito.when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("repoKey"));

        Assertions.assertEquals(shouldRedirect, shouldRedirectToUiNativeBrowserRelativeToAuthChain(
                request, beforeAuthChain));
    }

    @ParameterizedTest
    @MethodSource("provideRequests")
    public void testRedirectToNewNativeBrowser(String requestUri, String expectedRepoPath) {
        CentralConfigDescriptor configDescriptor = Mockito.mock(CentralConfigDescriptor.class);
        Mockito.when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        Mockito.when(configDescriptor.getJFrogUrlBase(0)).thenReturn("http://localhost:8081/");
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = new MockHttpServletResponse();
        Mockito.when(request.getScheme()).thenReturn("http");
        Mockito.when(request.getContextPath()).thenReturn("artifactory");
        Mockito.when(request.getRequestURI()).thenReturn(requestUri);

        redirectToNewNativeBrowser(request, response);
        String location = response.getHeader(HttpHeaders.LOCATION);
        Assertions.assertEquals("http://localhost:8081/ui/native/" + expectedRepoPath, location);
        Assertions.assertEquals(SC_MOVED_TEMPORARILY, response.getStatus());
    }

    @ParameterizedTest
    @MethodSource("provideURIs")
    public void testIsBinaryStoreServletRequest(String servletContextPath, String requestURI,
            boolean isBinaryStoreServletExpectedValue) {
        assertEquals(ServletUtils.isBinaryStoreServletRequest(servletContextPath, requestURI),
                isBinaryStoreServletExpectedValue);
    }

    static Stream<Arguments> provideRequests() {
        return Stream.of(
                Arguments.of("artifactory/repoKey/somePath", "repoKey/somePath"),
                Arguments.of("artifactory/list/repoKey/somePath", "repoKey/somePath"),
                Arguments.of("artifactory/simple/repoKey/somePath", "repoKey/somePath"),
                Arguments.of("artifactory/repoKey", "repoKey"),
                Arguments.of("artifactory/a", "a"),
                Arguments.of("artifactory/", "")
        );
    }

    static Stream<Arguments> provideUserAgent() {
        String chromeUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36";
        String powerShellUserAgent =
                "Mozilla/5.0 (Windows NT 10.0; Microsoft Windows 10.0.15063; en-US) PowerShell/6.0.0";
        return Stream.of(
                Arguments.of(chromeUserAgent, "repoKey/somePath/", false, true),
                Arguments.of(chromeUserAgent, "repoKey/somePath/", false, true),
                Arguments.of("Curl", "repoKey/somePath/", false, false),
                Arguments.of("", "repoKey/somePath/", false, false),
                Arguments.of(chromeUserAgent, "repoKey/somePath", true, true),
                Arguments.of(chromeUserAgent, "repoKey/", false, true),
                Arguments.of(chromeUserAgent, "repoKey", true, true),
                Arguments.of(chromeUserAgent, "", false, false),
                Arguments.of(powerShellUserAgent, "repoKey/somePath/", false, false),
                Arguments.of(powerShellUserAgent, "repoKey/somePath/", true, false),
                Arguments.of(powerShellUserAgent, "", false, false)
        );
    }

    static Stream<Arguments> provideURIs() {
        return Stream.of(
                Arguments.of("/artifactory", "", false),
                Arguments.of("/artifactory", "/", false),
                Arguments.of("/artifactory", "/foo", false),
                Arguments.of("/artifactory", "artifactory", false),
                Arguments.of("/artifactory", "/artifactory", false),
                Arguments.of("/artifactory", "/artifactory/api/system/ping", false),
                Arguments.of("/artifactory", "binarystore.xml", false),
                Arguments.of("/artifactory", "artifactory/repo/binarystore.xml", false),
                Arguments.of("/artifactory", "/artifactory/binarystore", true),
                Arguments.of("", "/binarystore", true),
                Arguments.of("", "/binarystore/foo", true),
                Arguments.of("/artifactory", "/artifactory/binarystore", true),
                Arguments.of("/art", "/artifactory/binarystore", false)
        );
    }
}