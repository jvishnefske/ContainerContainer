package org.artifactory.security;

import org.artifactory.request.RequestThreadLocal;
import org.artifactory.request.RequestWrapper;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.props.auth.PropsAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

@Test
public class AuthenticationHelperTest {

    @SuppressWarnings("ConstantConditions")
    public void testGetRemoteAddress() {
        String remoteAddress = AuthenticationHelper.getRemoteAddress(null);
        assertNull(remoteAddress);

        Authentication authentication = mock(Authentication.class);
        Object object = new Object();
        when(authentication.getDetails()).thenReturn(object);
        remoteAddress = AuthenticationHelper.getRemoteAddress(authentication);
        assertNull(remoteAddress);

        WebAuthenticationDetails details = mock(WebAuthenticationDetails.class);
        when(details.getRemoteAddress()).thenReturn("addr");
        when(authentication.getDetails()).thenReturn(details);
        remoteAddress = AuthenticationHelper.getRemoteAddress(authentication);
        assertEquals(remoteAddress, "addr");

        authentication = mock(PropsAuthenticationToken.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        RequestThreadLocal.bind(new RequestWrapper(request));
        remoteAddress = AuthenticationHelper.getRemoteAddress(authentication);
        assertEquals(remoteAddress, "127.0.0.1");

        authentication = mock(AccessTokenAuthentication.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.2");
        remoteAddress = AuthenticationHelper.getRemoteAddress(authentication);
        assertEquals(remoteAddress, "127.0.0.2");
    }

}