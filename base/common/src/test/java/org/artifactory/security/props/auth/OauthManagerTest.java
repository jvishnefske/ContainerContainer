package org.artifactory.security.props.auth;

import com.google.common.collect.Lists;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.api.security.access.CreatedTokenInfo;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.api.security.access.UserTokenSpec;
import org.artifactory.common.ConstantValues;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.artifactory.security.props.auth.model.TokenManagerKey;
import org.artifactory.security.props.auth.model.TokenType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class OauthManagerTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String USERNAME = "user";
    private static final String TOKEN =
            "eyJ2ZXIiOiIyIiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYiLCJraWQiOiJYOWRISlA0eHFubDlwM" +
                    "VZPNWNUZFBDVEUxa3VKQVZlbTQ0dHVhZEtrQVcwIn0.eyJzdWIiOiJqZnJ0QDAxZnhxZGt" +
                    "ydDEzY3N3MWFrNmVxMzgxNnNmXC91c2Vyc1wvYWRtaW4iLCJzY3AiOiJhcHBsaWVkLXBlcm" +
                    "1pc3Npb25zXC91c2VyIiwiYXVkIjoiamZydEAwMWZ4cWRrcnQxM2NzdzFhazZlcTM4MTZzZi" +
                    "IsImlzcyI6ImpmcnRAMDFmeHFka3J0MTNjc3cxYWs2ZXEzODE2c2ZcL3VzZXJzXC9hZG1pbiIsI" +
                    "mV4cCI6MTY0NzE2MDg1MCwiaWF0IjoxNjQ3MTYwMjUwLCJqdGkiOiI0YmE2MGNmMy1mNDIwLTR" +
                    "jMjYtOTBkNS01ZTU0NGNmOTY3YzgifQ.Rv6PKXi3bXLNfkgar8eqGVLFEcIRYX_7UfAqL_HgL3d" +
                    "a2UOu7zkyuSlJNbzFAZQXpKqv5Kr3lVX2ShpaQOr6-7sVSt5VYZiYkxnHsnmDic2QZ1j6s2ZQQXd" +
                    "12pfxVgcGbgZpHtQtmlyFJ38KbTs5bCWoWCmCbDT1oXCImHLE8DzL9chlRnEn-fSWw_39z57v3QKc" +
                    "rd7Vgu-lbnagD5yc4c_4hDMYvbY8BgEE53N1J5i6GFdN2Qdj6grlORsWQnCBFvsLcjcBYaO-oOP5M" +
                    "ekf6HePK8ZcKUSMmQo1lj-RgixeL5Y_hXviuREf_7g5aTe-kpH_uW5IoCLOkHxC2sk4cQ";
    @Mock
    AccessService accessService;
    @Mock
    UserGroupService userGroupService;

    OauthManager oauthManager;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(userGroupService.isUserExists(USERNAME)).thenReturn(true);
        oauthManager = spy(new OauthManager());
        oauthManager.setAccessService(accessService);
        oauthManager.setUserGroupService(userGroupService);
        oauthManager.init();
        clearInvocations(oauthManager);
    }

    @AfterEach
    public void cleanup() throws Exception {
        verifyNoMoreInteractions(accessService);
        mocks.close();
    }

    @ParameterizedTest
    @EnumSource(value = TokenType.class, names = {"DOCKER", "CONAN"})
    void createToken(TokenType tokenType) {
        mockAccessService(false, tokenType);
        TokenKeyValue tokenKeyValue = oauthManager.createToken(new TokenManagerKey(USERNAME, tokenType));
        Assertions.assertEquals(OauthManager.OAUTH_KEY, tokenKeyValue.getKey());
        Assertions.assertEquals(TOKEN, tokenKeyValue.getToken());
        verify(accessService, times(1)).createToken(any(), eq(true), eq(false));
    }

    @ParameterizedTest
    @EnumSource(value = TokenType.class, names = {"DOCKER", "CONAN"})
    void createTokenReturnNull(TokenType tokenType) {
        mockAccessService(false, tokenType);
        doThrow(new RuntimeException("exception")).when(accessService).createToken(any(), eq(true), eq(false));
        TokenKeyValue tokenKeyValue = oauthManager.createToken(new TokenManagerKey(USERNAME, tokenType));
        assertNull(tokenKeyValue);
        verify(accessService, times(1)).createToken(any(), eq(true), eq(false));
    }

    @ParameterizedTest
    @EnumSource(value = TokenType.class, names = {"DOCKER", "CONAN"})
    void getToken(TokenType tokenType) {
        mockAccessService(false, tokenType);
        TokenManagerKey tokenManagerKey = new TokenManagerKey(USERNAME, tokenType);
        doReturn(new TokenKeyValue(OauthManager.OAUTH_KEY, TOKEN)).when(oauthManager)
                .createToken(tokenManagerKey);
        TokenKeyValue tokenKeyValue = oauthManager.getToken(tokenManagerKey);
        Assertions.assertEquals(TOKEN, tokenKeyValue.getToken());
        Assertions.assertEquals(OauthManager.OAUTH_KEY, tokenKeyValue.getKey());
        verify(oauthManager, times(1)).createToken(tokenManagerKey);
        TokenKeyValue cachedToken = oauthManager.getToken(tokenManagerKey);
        Assertions.assertEquals(tokenKeyValue, cachedToken);
        verify(oauthManager, times(2)).getToken(tokenManagerKey);
        verifyNoMoreInteractions(oauthManager);
    }

    @ParameterizedTest
    @EnumSource(value = TokenType.class, names = {"DOCKER", "CONAN"})
    void getExpiredTokenFromCache(TokenType tokenType) {
        mockAccessService(false, tokenType);
        TokenKeyValue createdToken = new TokenKeyValue(OauthManager.OAUTH_KEY, TOKEN);
        TokenManagerKey tokenManagerKey = new TokenManagerKey(USERNAME, tokenType);

        doReturn(createdToken).when(oauthManager).createToken(tokenManagerKey);
        TokenKeyValue tokenKeyValue = oauthManager.getToken(tokenManagerKey);
        Assertions.assertEquals(TOKEN, tokenKeyValue.getToken());
        Assertions.assertEquals(OauthManager.OAUTH_KEY, tokenKeyValue.getKey());
        createdToken.setExpiresTimeStamp(System.currentTimeMillis());
        TokenKeyValue cachedToken = oauthManager.getToken(tokenManagerKey);
        Assertions.assertEquals(tokenKeyValue, cachedToken);
        verify(oauthManager, times(2)).getToken(tokenManagerKey);
        verify(oauthManager, times(2)).createToken(tokenManagerKey);
        verifyNoMoreInteractions(oauthManager);
    }

    @ParameterizedTest
    @EnumSource(value = TokenType.class, names = {"DOCKER", "CONAN"})
    public void createTokenForExternalUserNoGroups(TokenType tokenType) {
        mockAccessService(true, tokenType);
        TokenManagerKey tokenManagerKey = new TokenManagerKey(USERNAME, tokenType);
        when(userGroupService.isUserExists(USERNAME)).thenReturn(false);
        UserInfo userInfo = mock(UserInfo.class);
        when(userGroupService.currentUser()).thenReturn(userInfo);
        TokenKeyValue tokenKeyValue = oauthManager.createToken(tokenManagerKey);
        Assertions.assertNotNull(tokenKeyValue);
        Assertions.assertEquals(tokenKeyValue.getToken(), TOKEN);
        Assertions.assertEquals(tokenKeyValue.getKey(), "accesstoken");
        verify(accessService, times(1)).createToken(any(), eq(true), eq(false));
    }

    @Test
    public void getScope() {
        String memberOfGroups = "applied-permissions/groups:";
        UserInfo userInfo = mock(UserInfo.class);
        String scope = oauthManager.getScope(userInfo);
        Assertions.assertEquals(scope, memberOfGroups + "*");
        // Check with groups
        Set<UserGroupInfo> groupInfos = new HashSet<>();
        createGroupInfo(groupInfos, "info1");
        createGroupInfo(groupInfos, "info2");
        createGroupInfo(groupInfos, "info3");

        when(userInfo.getGroups()).thenReturn(groupInfos);
        scope = oauthManager.getScope(userInfo);
        Assertions.assertTrue(scope.contains(memberOfGroups));
        Assertions.assertTrue(scope.contains("info1"));
        Assertions.assertTrue(scope.contains("info2"));
        Assertions.assertTrue(scope.contains("info3"));

        String groups = scope.substring(memberOfGroups.length());
        Assertions.assertEquals(groups.split(",").length, 3);
    }

    private void createGroupInfo(Set<UserGroupInfo> groupInfos, String groupName) {
        UserGroupInfo userGroupInfo = mock(UserGroupInfo.class);
        when(userGroupInfo.toString()).thenReturn(groupName);
        groupInfos.add(userGroupInfo);

    }

    private void mockAccessService(boolean externalUser, TokenType tokenType) {
        CreatedTokenInfo createdTokenInfo = mock(CreatedTokenInfo.class);
        when(createdTokenInfo.getTokenValue()).thenReturn(TOKEN);
        doAnswer(invocationOnMock -> {
            TokenSpec tokenSpec = invocationOnMock.getArgument(0);
            UserTokenSpec expectedTokenSpec;
            if (!externalUser) {
                expectedTokenSpec = UserTokenSpec.create(USERNAME)
                        .expiresIn(tokenType.equals(TokenType.DOCKER) ?
                                ConstantValues.dockerTokensExpirationTimeSecs.getLong() :
                                ConstantValues.genericTokensExpirationTimeSecs.getLong())
                        .refreshable(false)
                        .description("token for " + tokenType.name().toLowerCase() + " operation")
                        .scope(OauthManager.TOKEN_SCOPES);
            } else {
                expectedTokenSpec = UserTokenSpec.create(USERNAME)
                        .expiresIn(ConstantValues.genericTokensCacheIdleTimeSecs.getLong())
                        .refreshable(false)
                        .description("token for " + tokenType.name().toLowerCase() + " operation")
                        .scope(Lists.newArrayList("applied-permissions/groups:*"));
            }
            assert tokenSpec.toString().equals(expectedTokenSpec.toString());
            return createdTokenInfo;
        }).when(accessService).createToken(any(), eq(true), eq(false));
    }
}