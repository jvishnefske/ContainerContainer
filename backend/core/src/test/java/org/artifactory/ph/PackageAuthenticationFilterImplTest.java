package org.artifactory.ph;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.authentication.ArtifactoryBasicAuthenticationFilter;
import org.artifactory.authentication.context.AuthenticationContext;
import com.jfrog.ph.acl.model.PackageAuthenticationRequestContext;
import com.jfrog.ph.acl.service.PackageAuthenticationAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.stream.Stream;

import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.artifactory.authentication.context.AuthenticationContextStatus.ANONYMOUS_ALLOWED;
import static org.artifactory.authentication.context.AuthenticationContextStatus.AUTHENTICATION_ALLOWED;
import static org.artifactory.authentication.context.AuthenticationContextStatus.FORCE_ANONYMOUS;
import static org.artifactory.authentication.context.AuthenticationContextStatus.HAS_ERROR;
import static org.artifactory.authentication.context.AuthenticationContextStatus.NOT_HANDLED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author shahafg
 */
class PackageAuthenticationFilterImplTest {

    private static final String BASIC_AUTH_VALUE = "Basic YWRtaW46cGFzc3dvcmQ=";
    private static final String PRINCIPLE_VALUE = "admin";

    private PackageAuthenticationAdapter packageAuthenticationAdapter;
    private PackageAuthenticationFilterImpl packageFilter;

    @BeforeEach
    public void setUp() {
        this.packageAuthenticationAdapter = Mockito.mock(PackageAuthenticationAdapter.class);
        this.packageFilter = new PackageAuthenticationFilterImpl(packageAuthenticationAdapter);
    }

    @AfterEach
    public void tearDown() {
        this.packageFilter = null;
        this.packageAuthenticationAdapter = null;
    }

    @Test
    void init() throws ServletException {
        FilterConfig filterConfig = Mockito.mock(FilterConfig.class);
        ServletContext servletContext = Mockito.mock(ServletContext.class);
        ArtifactoryContext artifactoryContext = Mockito.mock(ArtifactoryContext.class);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(artifactoryContext);
        when(artifactoryContext.beanForType(ArtifactoryBasicAuthenticationFilter.class)).thenReturn(
                Mockito.mock(ArtifactoryBasicAuthenticationFilter.class));
        packageFilter.init(filterConfig);
        verify(artifactoryContext, times(1)).beanForType(ArtifactoryBasicAuthenticationFilter.class);
    }

    private static Stream<Arguments> authenticationFlagsProvider() {
        return Stream.of(Arguments.of(true, false, false, false),
                Arguments.of(false, true, false, false),
                Arguments.of(false, false, true, false),
                Arguments.of(false, false, false, true),
                Arguments.of(true, false, false, true),
                Arguments.of(false, true, false, true),
                Arguments.of(false, false, false, false));
    }

    @Test
    void doFilter() throws ServletException, IOException {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        ArtifactoryBasicAuthenticationFilter basicFilter = Mockito.mock(ArtifactoryBasicAuthenticationFilter.class);
        doNothing().when(basicFilter).doFilter(any(), any(), any());
        initFilter(basicFilter);
        packageFilter.doFilter(request, Mockito.mock(HttpServletResponse.class), Mockito.mock(FilterChain.class));
        verify(basicFilter, times(1)).doFilter(any(), any(), any());
    }

    private void assertArtifactoryFilterContext(AuthenticationContext expected, AuthenticationContext actual) {
        Assertions.assertEquals(expected.getAuthenticationStatus(), actual.getAuthenticationStatus());
        Assertions.assertEquals(expected.getExtractedAuthorizationToken(), actual.getExtractedAuthorizationToken());
        Assertions.assertEquals(expected.getLoginIdentifier(), actual.getLoginIdentifier());
        Assertions.assertEquals(expected.getCacheKey(), actual.getCacheKey());
    }

    private void initFilter(ArtifactoryBasicAuthenticationFilter basicFilter) throws ServletException {
        FilterConfig filterConfig = Mockito.mock(FilterConfig.class);
        ServletContext servletContext = Mockito.mock(ServletContext.class);
        ArtifactoryContext artifactoryContext = Mockito.mock(ArtifactoryContext.class);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(artifactoryContext);
        when(artifactoryContext.beanForType(ArtifactoryBasicAuthenticationFilter.class)).thenReturn(basicFilter);
        packageFilter.init(filterConfig);
    }

    @ParameterizedTest
    @MethodSource("authenticationFlagsProvider")
    void testGetAuthenticationContext(boolean isAuthenticated, boolean isAnonymousAllowed, boolean hasError,
            boolean isForceAnonymous) throws ServletException, IOException {
        initFilter(new ArtifactoryBasicAuthenticationFilter());
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        PackageAuthenticationRequestContext context =
                getRequestContext(isAuthenticated, isAnonymousAllowed, hasError, isForceAnonymous);
        AuthenticationContext expected =
                buildFilterContext(isAuthenticated, isAnonymousAllowed, hasError, isForceAnonymous);
        when(packageAuthenticationAdapter.getContext(request)).thenReturn(context);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        PrintWriter writer = Mockito.mock(PrintWriter.class);
        doNothing().when(response).setStatus(anyInt());
        doNothing().when(response).setHeader(any(), any());
        when(response.getWriter()).thenReturn(writer);
        doNothing().when(writer).write(anyString());
        assertArtifactoryFilterContext(expected, packageFilter.getAuthenticationContext(request, response));
        if (expected.getAuthenticationStatus() == HAS_ERROR) {
            verify(response, times(1)).getWriter();
            verify(writer, times(1)).write(anyString());
            verify(response, times(1)).setStatus(anyInt());
            verify(response, times(1)).addHeader(any(), any());
        }
    }

    private AuthenticationContext buildFilterContext(boolean isAuthenticated, boolean isAnonymousAllowed,
            boolean hasError, boolean isForceAnonymous) {
        if (isForceAnonymous) {
            return AuthenticationContext.builder().authenticationStatus(FORCE_ANONYMOUS).build();
        } else if (isAuthenticated && !isAnonymousAllowed) {
            return AuthenticationContext.builder().authenticationStatus(AUTHENTICATION_ALLOWED)
                    .extractedAuthorizationToken(BASIC_AUTH_VALUE).loginIdentifier(PRINCIPLE_VALUE)
                    .cacheKey(BASIC_AUTH_VALUE).build();
        } else if (!isAuthenticated && isAnonymousAllowed) {
            return AuthenticationContext.builder().authenticationStatus(ANONYMOUS_ALLOWED).build();
        } else if (hasError) {
            return AuthenticationContext.builder().authenticationStatus(HAS_ERROR).build();
        } else {
            return AuthenticationContext.builder().authenticationStatus(NOT_HANDLED).build();
        }
    }

    private PackageAuthenticationRequestContext getRequestContext(boolean isAuthenticated, boolean isAnonymousAllowed,
            boolean hasError, boolean isForceAnonymous) {
        if (isForceAnonymous) {
            return PackageAuthenticationRequestContext.builder().forceAnonymousRequest(true).build();
        } else if (isAuthenticated && !isAnonymousAllowed) {
            return PackageAuthenticationRequestContext.builder().authenticationAllowed(true)
                    .anonymousRequestAllowed(false).authorizationToken(BASIC_AUTH_VALUE)
                    .principleUserName(PRINCIPLE_VALUE).build();
        } else if (!isAuthenticated && isAnonymousAllowed) {
            return PackageAuthenticationRequestContext.builder().anonymousRequestAllowed(true).build();
        }

        if (hasError) {
            return PackageAuthenticationRequestContext.builder().responseError(buildUnAuthorizedResponse()).build();
        }
        return PackageAuthenticationRequestContext.builder().build();
    }

    private static Response buildUnAuthorizedResponse() {
        return Response.status(SC_UNAUTHORIZED).entity(new Object())
                .header("WWW-Authenticate", "Basic realm=\"Artifactory Realm\"").build();
    }
}