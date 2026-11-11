package org.artifactory.security.access;

import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.access.providers.AccessTokenAuthenticationProviderV2;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.artifactory.security.props.auth.BadPropsAuthException;
import org.artifactory.security.props.auth.PropsAuthenticationToken;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.auth.AccessPrincipal;
import org.jfrog.access.client.auth.LoginResult;
import org.jfrog.access.client.auth.ServicePrincipal;
import org.jfrog.access.client.auth.UserPrincipal;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.access.common.token.VerifyResultAttributes;
import org.jfrog.access.model.UserStatus;
import org.jfrog.access.rest.group.GroupResponse;
import org.jfrog.access.rest.user.UserWithGroupsResponse;
import org.jfrog.access.token.AccessTokenType;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.JwtAccessTokenImpl;
import org.jfrog.access.user.UserMFAStatus;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

@Test
public class AccessTokenAuthenticationProviderV2Test extends ArtifactoryHomeBoundTest {

    private AccessTokenAuthenticationProviderV2 provider;

    @Mock
    private AccessService accessService;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);

        provider = new AccessTokenAuthenticationProviderV2(accessService);
        ServiceId serviceId = new ServiceId("jfrt", "xyz");
        when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void testAuthenticateReferenceToken() {
        when(accessService.getAccessTokenType(any())).thenReturn(AccessTokenType.REFERENCE_TOKEN);
        when(accessService.ensureAuth(any())).thenReturn(
                new LoggedInResult(new UserPrincipal(new UserWithGroupsResponse("user").status(UserStatus.ENABLED)
                        .mfaStatus(UserMFAStatus.NONE),
                        Collections.emptySet(),
                        Collections.emptySet())));
        String refToken = "cmVmdGtuOjAxOjE3MDMxNTQ1MzM6cWVuQzNKN1BBMWZxb29JZ21kQ2lzU2NqY3lj";
        when(accessService.verifyAndGetResult(refToken)).thenReturn(new TokenResult());
        Authentication authenticate = provider.authenticate(new AccessTokenAuthentication(refToken, "user", null));
        assertTrue(authenticate instanceof AccessTokenAuthentication);
        assertEquals(((AccessTokenAuthentication) authenticate).getAccessToken(), TokenResult.JWT_TOKEN);
        assertEquals(((AccessTokenAuthentication) authenticate).getAccessTokenValue(), refToken);
        Object principal = authenticate.getPrincipal();
        assertTrue(principal instanceof SimpleUser);
        assertEquals(((SimpleUser) principal).getUsername(), "user");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testAuthenticateReferenceTokenNullToken() {
        when(accessService.getAccessTokenType(any())).thenReturn(AccessTokenType.REFERENCE_TOKEN);
        when(accessService.ensureAuth(any())).thenReturn(
                new LoggedInResult(new UserPrincipal(new UserWithGroupsResponse("user").status(UserStatus.ENABLED)
                        .mfaStatus(UserMFAStatus.NONE),
                        Collections.emptySet(),
                        Collections.emptySet())));
        String refToken = "cmVmdGtuOjAxOjE3MDMxNTQ1MzM6cWVuQzNKN1BBMWZxb29JZ21kQ2lzU2NqY3lj";
        when(accessService.verifyAndGetResult(refToken)).thenReturn(new TokenVerifyResult() {
            @Nullable
            @Override
            public JwtAccessToken getAccessToken() {
                return null;
            }

            @Override
            public boolean isSuccessful() {
                return false;
            }

            @Nonnull
            @Override
            public String getReason() {
                return null;
            }

            @Nonnull
            @Override
            public String getReasonFullMessage() {
                return null;
            }

            @Nonnull
            @Override
            public Map<VerifyResultAttributes, String> getAttributes() {
                return Map.of();
            }
        });
        Authentication authenticate = provider.authenticate(new AccessTokenAuthentication(refToken, "user", null));
        assertTrue(authenticate instanceof AccessTokenAuthentication);
        assertEquals(((AccessTokenAuthentication) authenticate).getAccessToken(), TokenResult.JWT_TOKEN);
        assertEquals(((AccessTokenAuthentication) authenticate).getAccessTokenValue(), refToken);
        Object principal = authenticate.getPrincipal();
        assertTrue(principal instanceof SimpleUser);
        assertEquals(((SimpleUser) principal).getUsername(), "user");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testAuthenticateReferenceTokenBadTokenType() {
        when(accessService.getAccessTokenType(any())).thenReturn(AccessTokenType.REFERENCE_TOKEN)
                .thenReturn(AccessTokenType.NOT_VALID_TOKEN);
        when(accessService.ensureAuth(any())).thenReturn(
                new LoggedInResult(new UserPrincipal(new UserWithGroupsResponse("user").status(UserStatus.ENABLED)
                        .mfaStatus(UserMFAStatus.NONE),
                        Collections.emptySet(),
                        Collections.emptySet())));
        String refToken = "cmVmdGtuOjAxOjE3MDMxNTQ1MzM6cWVuQzNKN1BBMWZxb29JZ21kQ2lzU2NqY3lj";
        when(accessService.verifyAndGetResult(refToken)).thenReturn(new TokenResult());
        provider.authenticate(new AccessTokenAuthentication(refToken, "user", null));
    }

    @Test(expectedExceptions = AccessTokenAuthenticationException.class)
    public void testAuthenticateReferenceTokenUnauthenticated() {
        when(accessService.getAccessTokenType(any())).thenReturn(AccessTokenType.REFERENCE_TOKEN);
        when(accessService.ensureAuth(any())).thenReturn(
                new LoggedInResult(new UserPrincipal(new UserWithGroupsResponse("user").status(UserStatus.ENABLED)
                        .mfaStatus(UserMFAStatus.NONE),
                        Collections.emptySet(),
                        Collections.emptySet()), false));
        String refToken = "cmVmdGtuOjAxOjE3MDMxNTQ1MzM6cWVuQzNKN1BBMWZxb29JZ21kQ2lzU2NqY3lj";
        provider.authenticate(new AccessTokenAuthentication(refToken, "user", null));
    }

    @Test(expectedExceptions = AuthenticationServiceException.class)
    public void testAuthenticateUnsupportedPrincipal() {
        when(accessService.getAccessTokenType(any())).thenReturn(AccessTokenType.REFERENCE_TOKEN);
        when(accessService.ensureAuth(any())).thenReturn(
                new LoggedInResult(new AccessPrincipal() {
                    @Nonnull
                    @Override
                    public String getName() {
                        return "wrongprincipal";
                    }
                }));
        String refToken = "cmVmdGtuOjAxOjE3MDMxNTQ1MzM6cWVuQzNKN1BBMWZxb29JZ21kQ2lzU2NqY3lj";
        provider.authenticate(new AccessTokenAuthentication(refToken, "user", null));
    }

    @Test
    public void testAuthenticateToken() {
        when(accessService.getAccessTokenType(any())).thenReturn(AccessTokenType.JWT_ACCESS_TOKEN);
        when(accessService.ensureAuth(any())).thenReturn(
                new LoggedInResult(new UserPrincipal(new UserWithGroupsResponse("user").status(UserStatus.ENABLED)
                        .mfaStatus(UserMFAStatus.NONE),
                        Collections.emptySet(),
                        Collections.emptySet())));
        when(accessService.parseToken(any())).thenReturn(TokenResult.JWT_TOKEN);
        Authentication authenticate =
                provider.authenticate(new AccessTokenAuthentication(TokenResult.JWT_TOKEN, "user", null));
        assertTrue(authenticate instanceof AccessTokenAuthentication);
        assertEquals(((AccessTokenAuthentication) authenticate).getAccessTokenValue(), TokenResult.TOKEN_VALUE);
        Object principal = authenticate.getPrincipal();
        assertTrue(principal instanceof SimpleUser);
        assertEquals(((SimpleUser) principal).getUsername(), "token:user");
    }

    @Test
    public void testAuthenticateServicePrincipal() {
        when(accessService.getAccessTokenType(any())).thenReturn(AccessTokenType.REFERENCE_TOKEN);
        ServiceId serviceId = ServiceId.generateUniqueId("jfrtt");
        when(accessService.ensureAuth(any())).thenReturn(
                new LoggedInResult(new ServicePrincipal(serviceId, Collections.emptyList())));
        String refToken = "cmVmdGtuOjAxOjE3MDMxNTQ1MzM6cWVuQzNKN1BBMWZxb29JZ21kQ2lzU2NqY3lj";
        when(accessService.verifyAndGetResult(refToken)).thenReturn(new TokenResult());
        Authentication authenticate =
                provider.authenticate(new AccessTokenAuthentication(refToken, null, null));
        assertTrue(authenticate instanceof AccessTokenAuthentication);
        assertEquals(((AccessTokenAuthentication) authenticate).getAccessToken(), TokenResult.JWT_TOKEN);
        assertEquals(((AccessTokenAuthentication) authenticate).getAccessTokenValue(), refToken);
        Object principal = authenticate.getPrincipal();
        assertTrue(principal instanceof SimpleUser);
        assertEquals(((SimpleUser) principal).getUsername(), serviceId.getFormattedName());
    }

    @Test(expectedExceptions = BadPropsAuthException.class)
    public void testAuthenticateJwtApiKey() {
        when(accessService.getAccessTokenType(any())).thenReturn(AccessTokenType.JWT_ACCESS_TOKEN);
        PropsAuthenticationToken token =
                new PropsAuthenticationToken("user", ApiKeyManager.API_KEY, TokenResult.TOKEN_VALUE,
                        Collections.emptySet());
        provider.authenticate(token);
    }

    @Test(expectedExceptions = AccessTokenAuthenticationException.class)
    public void testAccessClientExceptionIsWrappedAsAuthenticationException() {
        when(accessService.getAccessTokenType(any())).thenReturn(AccessTokenType.REFERENCE_TOKEN);
        when(accessService.ensureAuth(any())).thenThrow(new AccessClientException("Access error"));
        String refToken = "cmVmdGtuOjAxOjE3MDMxNTQ1MzM6cWVuQzNKN1BBMWZxb29JZ21kQ2lzU2NqY3lj";
        when(accessService.verifyAndGetResult(refToken)).thenReturn(new TokenResult());
        provider.authenticate(new AccessTokenAuthentication(refToken, null, null));
    }

    @Test(expectedExceptions = AccessTokenAuthenticationException.class)
    public void testAccessClientExceptionFromBearerTokenIsWrappedAsAuthenticationException() {
        when(accessService.getAccessTokenType(any())).thenReturn(AccessTokenType.JWT_ACCESS_TOKEN);
        when(accessService.ensureAuth(any())).thenThrow(
                new AccessClientException("Failed to verify signed access token"));
        PropsAuthenticationToken bearerToken =
                new PropsAuthenticationToken(null, "oauth2", "hs256.bearer.token", Collections.emptySet());
        provider.authenticate(bearerToken);
    }


    private List<GroupResponse> getUserGroupResponses() {
        List<GroupResponse> userGroups = Lists.newArrayList();
        userGroups.add(new GroupResponse().name("readers"));
        return userGroups;
    }

    @Test
    public void testTransientUser() {
        when(accessService.getAccessTokenType(any())).thenReturn(AccessTokenType.REFERENCE_TOKEN);
        LoggedInResult loggedInResult = new LoggedInResult(new UserPrincipal(
                new UserWithGroupsResponse("randomUser1233432")
                .groups(getUserGroupResponses())
                .status(UserStatus.ENABLED)
                .mfaStatus(UserMFAStatus.NONE)
                .transientUser(true),
                getTokenScopes(),
                Collections.emptySet()));

        String refToken = TokenResultForTransientUser.TOKEN_VALUE;
        when(accessService.ensureAuth(any())).thenReturn(loggedInResult);
        when(accessService.verifyAndGetResult(refToken)).thenReturn(new TokenResultForTransientUser());

        Authentication authenticate = provider.authenticate(new AccessTokenAuthentication(refToken, null, null));
        assertTrue(authenticate instanceof AccessTokenAuthentication);
        assertEquals(((AccessTokenAuthentication) authenticate).getAccessToken(),
                TokenResultForTransientUser.JWT_TOKEN);
        assertEquals(((AccessTokenAuthentication) authenticate).getAccessTokenValue(), refToken);
        Object principal = authenticate.getPrincipal();
        assertTrue(principal instanceof SimpleUser);
        assertEquals(((SimpleUser) principal).getUsername(), "randomUser1233432");
        assertEquals(((SimpleUser) principal).isTransientUser(), true);
    }

    private Set<String> getTokenScopes() {
        Set<String> hashSet = new HashSet<>();
        hashSet.add("member-of-groups:readers");
        return hashSet;
    }

    @Test
    public void testDefaultValues() {
        when(accessService.shouldDelegateTokenOrApiKeyAuthenticationToAccess()).thenReturn(true);
        assertNull(provider.getRealm());
        assertFalse(provider.userExists("user"));
        assertTrue(provider.supports(AccessTokenAuthentication.class));
    }

    private static class LoggedInResult implements LoginResult {

        private final AccessPrincipal principal;
        private final boolean authenticated;
        private final String reason;

        private LoggedInResult(AccessPrincipal principal) {
            this(principal, true);
        }

        private LoggedInResult(AccessPrincipal principal, boolean authenticated) {
            this.principal = principal;
            this.authenticated = authenticated;
            reason = authenticated ? null : "non authenticated";
        }

        @Override
        public AccessPrincipal getPrincipal() {
            return principal;
        }

        @Override
        public boolean isSuccessful() {
            return authenticated;
        }

        @Nonnull
        @Override
        public String getReason() {
            return reason;
        }

        @Override
        public String getCookie() {
            return null;
        }

        @Override
        public String toString() {
            return super.toString();
        }
    }

    private static class TokenResultForTransientUser implements TokenVerifyResult {
        static final String TOKEN_VALUE = """
                eyJ2ZXIiOiIyIiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYiLCJraWQiOi
                JsVnBLVzVwWGFwRXdmamsycmJRQTRLV09ENlRpRk9ERV83Xzh6R3JQYmFV
                In0.eyJzdWIiOiJqZnJ0QDAxaHJkNWN4anIwN3p5MXlibTBjOWMxMHQxL3
                VzZXJzL3JhbmRvbVVzZXIxMjMzNDMyIiwic2NwIjoibWVtYmVyLW9mLWdy
                b3VwczpyZWFkZXJzIiwiYXVkIjoiamZydEAwMWhyZDVjeGpyMDd6eTF5Ym
                0wYzljMTB0MSIsImlzcyI6ImpmcnRAMDFocmQ1Y3hqcjA3enkxeWJtMGM5
                YzEwdDEvdXNlcnMvYWRtaW4iLCJleHAiOjE3NDE2NDcxMjksImlhdCI6MT
                cxMDExMTEyOSwianRpIjoiNDNhMGY5ZjMtZTE3YS00MmU4LTkwYWItZTI1
                ODFjYWI1YzUzIn0.0-rcl4YLmpEa_Pde0Nka1JByEKOYErHkPlSdmk7SGw
                4LtPJ-3uvzQmy6tMJC8jpYSNy2VKnIsQaIhQkkckghiwAWWUMAcEAc22Zi
                o_newOfo3LI500c4aByWEjYFbow7FWh2uPC3-nu-A7WVlkCpqaZixGCTfY
                mhxQlwmABToY3QlBkI0xBAOYYTpGogqLmUsaulRJTGUQCVitrrDKW0B-We
                _-mH_4kPUkP-ycQbDucOqcLme7kU3o-05BXFEvfLAg8uB0sDAwGU89cBJO
                Gef2VwksLWDYXY5xpXNlzqRvnGayUKh8hDKay7701pQnNEkP3QsEKAqOMP
                ziYwsrmL8w
                """;
        static final JwtAccessToken JWT_TOKEN = JwtAccessTokenImpl.parseTokenValue(TOKEN_VALUE);

        @Nullable
        @Override
        public JwtAccessToken getAccessToken() {
            return JWT_TOKEN;
        }

        @Override
        public boolean isSuccessful() {
            return true;
        }

        @Nonnull
        @Override
        public String getReason() {
            return null;
        }

        @Nonnull
        @Override
        public String getReasonFullMessage() {
            return null;
        }

        @Nonnull
        @Override
        public Map<VerifyResultAttributes, String> getAttributes() {
            return Map.of();
        }
    }

    private static class TokenResult implements TokenVerifyResult {
        static final String TOKEN_VALUE = """
                eyJ2ZXIiOiIyIiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYiLCJraWQiOiJ5SjZmWGp6TnNoYjJud3BpSkc1X2xHQm1nWTN0Q2llUkh
                3ZFJmb3ZJZ2ZJIn0.eyJzdWIiOiJqZmFjQDAxaGZwcmprMmMwa2YyMXlteGNyNGgxbTNrL3VzZXJzL3JvZWlvIiwic2NwIjoiYXBwbG
                llZC1wZXJtaXNzaW9ucy91c2VyIiwiYXVkIjoiKkAqIiwiaXNzIjoiamZhY0AwMWhmcHJqazJjMGtmMjF5bXhjcjRoMW0zayIsImV4c
                CI6MTcwMTM3NzMzNSwiaWF0IjoxNzAxMzYyOTM1LCJqdGkiOiI1Y2FhMjljMi1mNjk5LTRlMjgtYjJmNy03OWExYTM2MzZkODkifQ.d
                i805wwBbwzPXpOWH63JBI8sq27OdS_xG8MF-gVz_Cjnydj-QnC1Yz6dsy6ONegsD4iU1LlmmNfqc-qzcqNvX0yhtPI8sylO6rwRsyCW
                nYdPpCj17NPObP7NSY5kcBbMYyfPKNN2kCzE5X3NkrewO0KTrMnEx9yYPP8Pf83Ob6rwUctTvIF2UaBq2UfguKy5znDEJq-ZFmrGaF-
                CSuVRiskFQd_sFG0_qvXZTyT35CvVWWM0Z8CBA8lX3ukrpTzRb-rhtzbEWjjAPd5zCdzt3dpWNZEIM8KQ2l7YcRlK4e_rukxYLm79sF
                w6gDHqIk3dqmxm8SCg8gpLaBItotQIOQ
                """;
        static final JwtAccessToken JWT_TOKEN = JwtAccessTokenImpl.parseTokenValue(TOKEN_VALUE);

        @Nullable
        @Override
        public JwtAccessToken getAccessToken() {
            return JWT_TOKEN;
        }

        @Override
        public boolean isSuccessful() {
            return true;
        }

        @Nonnull
        @Override
        public String getReason() {
            return null;
        }

        @Nonnull
        @Override
        public String getReasonFullMessage() {
            return null;
        }

        @Nonnull
        @Override
        public Map<VerifyResultAttributes, String> getAttributes() {
            return Map.of();
        }
    }
}