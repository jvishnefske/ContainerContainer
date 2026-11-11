package org.artifactory.security.utils;

import org.artifactory.security.access.AccessTokenAuthentication;
import org.jfrog.access.token.JwtAccessToken;
import org.mockito.Mockito;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Alexei Vainshtein
 */
public class AuthenticationUtilsTest {

    @Test(dataProvider = "providerXrayAuthenticationUser", description = "RTFACT-20177")
    public void testIsXrayAuthenticationUser(String subject, boolean expectedResult) {
        assertEquals(AuthenticationUtils.isXrayAuthenticationUser(subject, null), expectedResult);
    }

    @Test(dataProvider = "tokenAndExpectedResponse", description = "RTFACT-20177")
    public void testIsXrayAuthenticationUserWithAuthentication(String subject, boolean expectedResult) {
        AccessTokenAuthentication accessTokenAuthentication = Mockito.mock(AccessTokenAuthentication.class);
        JwtAccessToken jwtAccessToken = Mockito.mock(JwtAccessToken.class);
        when(accessTokenAuthentication.getAccessToken()).thenReturn(jwtAccessToken);
        when(jwtAccessToken.getSubject()).thenReturn(subject);
        assertEquals(AuthenticationUtils.isXrayAuthenticationUser("", accessTokenAuthentication), expectedResult);
    }

    @Test(dataProvider = "tokenAndExpectedResponse", description = "RTFACT-20177")
    public void testIsXrayTokenAuthentication(String subject, boolean expectedResult) {
        AccessTokenAuthentication accessTokenAuthentication = Mockito.mock(AccessTokenAuthentication.class);
        JwtAccessToken jwtAccessToken = Mockito.mock(JwtAccessToken.class);
        when(accessTokenAuthentication.getAccessToken()).thenReturn(jwtAccessToken);
        when(jwtAccessToken.getSubject()).thenReturn(subject);
        assertEquals(AuthenticationUtils.isXrayTokenAuthentication(accessTokenAuthentication), expectedResult);
    }

    @Test(dataProvider = "accessServiceTokenProvider")
    public void testIsAccessServiceTokenAuthentication(String subject, boolean expectedResult) {
        AccessTokenAuthentication accessTokenAuthentication = Mockito.mock(AccessTokenAuthentication.class);
        JwtAccessToken jwtAccessToken = Mockito.mock(JwtAccessToken.class);
        when(accessTokenAuthentication.getAccessToken()).thenReturn(jwtAccessToken);
        when(jwtAccessToken.getSubject()).thenReturn(subject);

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(accessTokenAuthentication);
        SecurityContextHolder.setContext(securityContext);

        assertEquals(AuthenticationUtils.isAccessServiceTokenAuthentication(), expectedResult);
    }

    @DataProvider(name = "accessServiceTokenProvider")
    public static Object[][] accessServiceTokenProvider() {
        return new Object[][] {
                {"jfac@token24", true},
                {"jf-access@token24", true},
                {"token:jfac@token24", true},
                {"admin@token24", false},
                {"jfxr@token24", false},
                {"", false}
        };
    }

    @DataProvider(name = "providerXrayAuthenticationUser")
    public static Object[][] providerXrayAuthenticationUser() {
        return new Object[][] {
                {"admin", false},
                {"_system_", false},
                {"xray", true},
        };
    }

    @DataProvider(name = "tokenAndExpectedResponse")
    public static Object[][] providerXrayTokenAuthentication() {
        return new Object[][] {
                {"admin", false},
                {"jfxr@token24", true},
                {"jfrt@token24", false},
                {"jffe@token24", false},
                {"jfxana@token24", true},
                {"jfxpst@token24", true},
                {"jfxidx@token24", true},
                {"jfno@token24", false},
                {"jfxr", false},
                {"", false}
        };
    }

}