package org.artifactory.webapp.servlet.authentication;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.security.SecurityService;
import org.artifactory.authentication.ArtifactoryAuthenticationContextFilter;
import org.artifactory.authentication.ArtifactoryAuthenticationType;
import org.artifactory.authentication.context.AuthenticationContext;
import org.artifactory.authentication.context.AuthenticationContextStatus;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.webapp.servlet.model.ArtifactoryFilterHolder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author shahafg
 */
class ArtifactoryAuthenticationFilterChainTest {
    /**
     * The test checks that a filter with a response with status of has error is not filtered.
     */
    @Test
    void testFilterHasErrorStatus() throws IOException {
        Assertions.assertNotNull(getAcceptedFilter(AuthenticationContextStatus.HAS_ERROR));
    }

    /**
     * The test checks that a filter with a response with status of authentication allowed is not filtered.
     */
    @Test
    void testFilterAuthenticationAllowedStatus() throws IOException {
        Assertions.assertNotNull(getAcceptedFilter(AuthenticationContextStatus.AUTHENTICATION_ALLOWED));
    }

    /**
     * The test checks that a filter with a response with status of anonymous allowed is not filtered.
     */
    @Test
    void testFilterAnonymousAllowed() throws IOException {
        Assertions.assertNotNull(getAcceptedFilter(AuthenticationContextStatus.ANONYMOUS_ALLOWED));
    }

    /**
     * The test checks that a filter with a response with status of not handled is filtered.
     */
    @Test
    void testFilterNotHandled() throws IOException {
        Assertions.assertNull(getAcceptedFilter(AuthenticationContextStatus.NOT_HANDLED));
    }

    private ArtifactoryFilterHolder getAcceptedFilter(AuthenticationContextStatus status) throws IOException {
        FilterConfig filterConfig = Mockito.mock(FilterConfig.class);
        ServletContext servletContext = Mockito.mock(ServletContext.class);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        ArtifactoryApplicationContext applicationContext = Mockito.mock(ArtifactoryApplicationContext.class);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY))
                .thenReturn(applicationContext);
        BasicAuthenticationEntryPoint basicAuthenticationEntryPoint = Mockito.mock(BasicAuthenticationEntryPoint.class);
        when(applicationContext.beanForType(BasicAuthenticationEntryPoint.class))
                .thenReturn(basicAuthenticationEntryPoint);
        SecurityService securityService = mock(SecurityService.class);
        when(applicationContext.beanForType(SecurityService.class)).thenReturn(securityService);
        ArtifactoryAuthenticationContextFilter packagesAuthenticationFilter =
                Mockito.mock(ArtifactoryAuthenticationContextFilter.class);
        when(packagesAuthenticationFilter.getAuthenticationContext(any(), any())).thenReturn(
                AuthenticationContext.builder().authenticationStatus(status).build());
        when(applicationContext.beansForType(ArtifactoryAuthenticationContextFilter.class)).thenReturn(
                Map.of("PackagesFilter", packagesAuthenticationFilter));
        when(packagesAuthenticationFilter.getType()).thenReturn(ArtifactoryAuthenticationType.PACKAGES_AUTH);
        ArtifactoryAuthenticationFilterChain chain =
                new ArtifactoryAuthenticationFilterChain(basicAuthenticationEntryPoint);
        List<ArtifactoryAuthenticationContextFilter> list = List.of(packagesAuthenticationFilter);
        chain.addFilters(Collections.emptyList(), list);
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/artifactory/api/packageName/someapi/");
        HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
        return chain.acceptFilter(req, resp);
    }
}