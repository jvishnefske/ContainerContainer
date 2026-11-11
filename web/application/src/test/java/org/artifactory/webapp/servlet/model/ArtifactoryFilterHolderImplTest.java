package org.artifactory.webapp.servlet.model;

import org.artifactory.authentication.ArtifactoryAuthenticationFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;

/**
 * @author shahafg
 */
class ArtifactoryFilterHolderImplTest {

    private ArtifactoryAuthenticationFilter filter;
    private ArtifactoryFilterHolderImpl filterHolder;

    @BeforeEach
    void setUp() {
        filter = Mockito.mock(ArtifactoryAuthenticationFilter.class);
        filterHolder = new ArtifactoryFilterHolderImpl(filter);
    }

    @Test
    void isAnonymousAllowed() {
        Assertions.assertFalse(filterHolder.isAnonymousAllowed());
    }

    @Test
    void getCacheKey() {
        filterHolder.getCacheKey(Mockito.mock(HttpServletRequest.class));
        Mockito.verify(filter, Mockito.times(1)).getCacheKey(any());
    }

    @Test
    void getLoginIdentifier() {
        filterHolder.getLoginIdentifier(Mockito.mock(HttpServletRequest.class));
        Mockito.verify(filter, Mockito.times(1)).getLoginIdentifier(any());
    }

    @Test
    void isAcceptedFilter() {
        filterHolder.isAcceptedFilter(Mockito.mock(HttpServletRequest.class));
        Mockito.verify(filter, Mockito.times(1)).acceptFilter(any());
    }

    @Test
    void doFilter() throws IOException, ServletException {
        filterHolder.doFilter(Mockito.mock(HttpServletRequest.class),
                Mockito.mock(HttpServletResponse.class), Mockito.mock(FilterChain.class));
        Mockito.verify(filter, Mockito.times(1)).doFilter(any(), any(), any());
    }

    @Test
    void doesRequireReAuthentication() {
        filterHolder.doesRequireReAuthentication(Mockito.mock(HttpServletRequest.class),
                Mockito.mock(Authentication.class));
        Mockito.verify(filter, Mockito.times(1)).requiresReAuthentication(any(), any());
    }

    @Test
    void testIsError() {
        Assertions.assertFalse(filterHolder.hasErrorResponse());
    }

    @Test
    void testIsForcedAnonymous() {
        Assertions.assertFalse(filterHolder.isForcedAnonymous());
    }

    @Test
    void getFilter() {
        Assertions.assertNotNull(filterHolder.getFilter());
    }
}