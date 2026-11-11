package org.artifactory.webapp.servlet.authentication;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.http.HttpHeaders;
import org.artifactory.authentication.ArtifactoryAuthenticationType;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import jakarta.servlet.ServletRequest;

import static org.artifactory.security.props.auth.OauthManager.OAUTH_IDN_TOKEN_NAME;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
class PropsAuthenticationFilterTest extends ArtifactoryHomeBoundJUnitTest  {

    private PropsAuthenticationFilter propsAuthenticationFilter;

    @Mock
    private HttpServletRequest request;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        propsAuthenticationFilter = new PropsAuthenticationFilter(null, null);
    }

    @AfterEach
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void getLoginIdentifier() {
        ServletRequest servletRequest = mock(ServletRequest.class);
        Assertions.assertNull(propsAuthenticationFilter.getLoginIdentifier(servletRequest));
    }

    @Test
    void getType() {
        BasicAuthenticationEntryPoint basicAuthenticationEntryPoint = Mockito.mock(BasicAuthenticationEntryPoint.class);
        AuthenticationManager authenticationManager = Mockito.mock(AuthenticationManager.class);
        PropsAuthenticationFilter propsAuthenticationFilter =
                new PropsAuthenticationFilter(authenticationManager, basicAuthenticationEntryPoint);
        Assertions.assertEquals(ArtifactoryAuthenticationType.PROPS_AUTH, propsAuthenticationFilter.getType());
    }

    @Test
    void acceptFilterWhenApiKey() {
        when(request.getHeader(ApiKeyManager.API_KEY_HEADER)).thenReturn("key-value");

        Assertions.assertTrue(propsAuthenticationFilter.acceptFilter(request));
    }

    @Test
    void acceptFilterWhenAuthHeader() {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token-value");

        Assertions.assertTrue(propsAuthenticationFilter.acceptFilter(request));
    }

    @Test
    void acceptFilterWhenTokenInCookie() {
        Cookie[] cookies = new Cookie[] {
                new Cookie(OAUTH_IDN_TOKEN_NAME, "token-value")
        };

        when(request.getCookies()).thenReturn(cookies);

        Assertions.assertTrue(propsAuthenticationFilter.acceptFilter(request));
    }

    @Test
    void acceptFilterWhenEmptyTokenInCookie() {
        Cookie[] cookies = new Cookie[] {
                new Cookie(OAUTH_IDN_TOKEN_NAME, "")
        };
        
        when(request.getCookies()).thenReturn(cookies);

        Assertions.assertFalse(propsAuthenticationFilter.acceptFilter(request));
    }

    @Test
    void acceptFilterWhenTokenNotFound() {
        Assertions.assertFalse(propsAuthenticationFilter.acceptFilter(request));
    }
}