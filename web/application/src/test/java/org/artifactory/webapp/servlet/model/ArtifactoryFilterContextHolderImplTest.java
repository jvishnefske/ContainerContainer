package org.artifactory.webapp.servlet.model;

import org.artifactory.authentication.ArtifactoryAuthenticationContextFilter;
import org.artifactory.authentication.context.AuthenticationContext;
import org.artifactory.model.security.UserImpl;
import org.artifactory.ph.PackageAuthenticationFilterImpl;
import org.artifactory.security.SimpleUser;
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

import static org.artifactory.authentication.context.AuthenticationContextStatus.ANONYMOUS_ALLOWED;
import static org.artifactory.authentication.context.AuthenticationContextStatus.AUTHENTICATION_ALLOWED;
import static org.artifactory.authentication.context.AuthenticationContextStatus.FORCE_ANONYMOUS;
import static org.artifactory.authentication.context.AuthenticationContextStatus.HAS_ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;


/**
 * @author shahafg
 */
class ArtifactoryFilterContextHolderImplTest {

    private static final String DUMMY_CACHE_KEY = "Dummy cache key";
    private static final String DUMMY_LOGIN_IDENTIFIER = "Dummy login Identifier";

    private ArtifactoryFilterContextHolderImpl filterHolder;
    private ArtifactoryAuthenticationContextFilter filter;
    private AuthenticationContext authenticationContext;

    @BeforeEach
    void setup() {
        filter = Mockito.mock(PackageAuthenticationFilterImpl.class);
        authenticationContext =
                AuthenticationContext.builder().authenticationStatus(AUTHENTICATION_ALLOWED).cacheKey(DUMMY_CACHE_KEY)
                        .extractedAuthorizationToken(DUMMY_CACHE_KEY)
                        .loginIdentifier(DUMMY_LOGIN_IDENTIFIER)
                        .build();
        filterHolder = new ArtifactoryFilterContextHolderImpl(filter, authenticationContext);
    }

    @Test
    void getCacheKey() {
        Assertions.assertEquals(DUMMY_CACHE_KEY, filterHolder.getCacheKey(Mockito.mock(HttpServletRequest.class)));
    }

    @Test
    void getLoginIdentifier() {
        Assertions.assertEquals(DUMMY_LOGIN_IDENTIFIER,
                filterHolder.getLoginIdentifier(Mockito.mock(HttpServletRequest.class)));
    }

    @Test
    void isAcceptedFilter() {
        Assertions.assertTrue(filterHolder.isAcceptedFilter(Mockito.mock(HttpServletRequest.class)));
    }

    @Test
    void isAnonymousAllowed() {
        AuthenticationContext authenticationContext =
                AuthenticationContext.builder().authenticationStatus(ANONYMOUS_ALLOWED).build();
        ArtifactoryFilterContextHolderImpl filterHolder =
                new ArtifactoryFilterContextHolderImpl(filter, authenticationContext);
        Assertions.assertTrue(filterHolder.isAnonymousAllowed());
    }

    @Test
    void isForcedAnonymous() {
        AuthenticationContext authenticationContext =
                AuthenticationContext.builder().authenticationStatus(FORCE_ANONYMOUS).build();
        ArtifactoryFilterContextHolderImpl filterHolder =
                new ArtifactoryFilterContextHolderImpl(filter, authenticationContext);
        Assertions.assertTrue(filterHolder.isForcedAnonymous());
    }

    @Test
    void doFilter() throws IOException, ServletException {
        doNothing().when(filter).doFilter(any(), any(), any());
        filterHolder.doFilter(Mockito.mock(HttpServletRequest.class), Mockito.mock(HttpServletResponse.class),
                Mockito.mock(FilterChain.class));
        Mockito.verify(filter, times(1)).doFilter(any(), any(), any());
    }

    @Test
    void doesRequireReAuthentication() {
        Authentication authentication = Mockito.mock(Authentication.class);
        SimpleUser user = new SimpleUser(new UserImpl(authenticationContext.getLoginIdentifier()));
        when(authentication.getPrincipal()).thenReturn(user);
        Assertions.assertFalse(
                filterHolder.doesRequireReAuthentication(Mockito.mock(HttpServletRequest.class), authentication));
        user = new SimpleUser(new UserImpl("Dummy"));
        when(authentication.getPrincipal()).thenReturn(user);
        Assertions.assertTrue(
                filterHolder.doesRequireReAuthentication(Mockito.mock(HttpServletRequest.class), authentication));
    }

    @Test
    void testHasErrorResponse() {
        AuthenticationContext authenticationContextWithError =
                AuthenticationContext.builder().authenticationStatus(HAS_ERROR).build();
        ArtifactoryFilterContextHolderImpl filterHolderWithError =
                new ArtifactoryFilterContextHolderImpl(filter, authenticationContextWithError);
        Assertions.assertTrue(filterHolderWithError.hasErrorResponse());
        Assertions.assertFalse(filterHolder.hasErrorResponse());
    }

    @Test
    void getAuthenticationContext() {
        Assertions.assertNotNull(filterHolder.getAuthenticationContext());
    }

    @Test
    void getFilter() {
        Assertions.assertNotNull(filterHolder.getFilter());
    }
}