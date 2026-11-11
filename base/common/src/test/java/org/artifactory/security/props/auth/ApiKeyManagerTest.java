package org.artifactory.security.props.auth;

import org.artifactory.api.security.UserGroupService;
import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.artifactory.security.props.auth.model.TokenManagerKey;
import org.artifactory.security.props.auth.model.TokenType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ApiKeyManagerTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String USERNAME = "user";
    private static final String TOKEN = "AKCp8krAQeqava5eRyoW9coyn4Rg749pFDsPhVMq4aorSY3ETfdoVzdDEPm5HBrc7xj8i4rpj";

    @Mock
    UserGroupService userGroupService;
    ApiKeyManager apiKeyManager;
    private AutoCloseable mocks;

    @BeforeEach
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        apiKeyManager = spy(new ApiKeyManager());
        apiKeyManager.setUserGroupService(userGroupService);
    }

    @AfterEach
    public void cleanup() throws Exception {
        verifyNoMoreInteractions(userGroupService);
        mocks.close();
    }

    @Test
    void createToken() {
        when(userGroupService.createPropsToken(eq(USERNAME), eq(ApiKeyManager.API_KEY), any())).thenReturn(true);
        TokenKeyValue tokenKeyValue = apiKeyManager.createToken(new TokenManagerKey(USERNAME, TokenType.APIKEY));
        Assertions.assertEquals(ApiKeyManager.API_KEY, tokenKeyValue.getKey());
        assertNotNull(tokenKeyValue.getToken());
        verify(userGroupService, times(1)).createPropsToken(any(), any(), any());
    }

    @Test
    void createTokenReturnNull() {
        when(userGroupService.createPropsToken(eq(USERNAME), eq(ApiKeyManager.API_KEY), any())).thenReturn(false);
        TokenKeyValue tokenKeyValue = apiKeyManager.createToken(new TokenManagerKey(USERNAME, TokenType.APIKEY));
        assertNull(tokenKeyValue);
        verify(userGroupService, times(1)).createPropsToken(any(), any(), any());
    }

    @Test
    void createTokenReturnNullDueToException() {
        doThrow(new RuntimeException("exception")).when(userGroupService).createPropsToken(any(), any(), any());
        TokenKeyValue tokenKeyValue = apiKeyManager.createToken(new TokenManagerKey(USERNAME, TokenType.APIKEY));
        assertNull(tokenKeyValue);
        verify(userGroupService, times(1)).createPropsToken(any(), any(), any());
    }

    @Test
    void getToken() {
        when(userGroupService.getPropsToken(USERNAME, ApiKeyManager.API_KEY)).thenReturn(TOKEN);
        TokenKeyValue tokenKeyValue = apiKeyManager.getToken(new TokenManagerKey(USERNAME, TokenType.APIKEY));
        assertEquals(TOKEN, tokenKeyValue.getToken());
        assertEquals(ApiKeyManager.API_KEY, tokenKeyValue.getKey());
        verify(userGroupService, times(1)).getPropsToken(USERNAME, ApiKeyManager.API_KEY);
    }

    @Test
    void getTokenReturnNull() {
        when(userGroupService.getPropsToken(USERNAME, ApiKeyManager.API_KEY)).thenReturn(null);
        TokenKeyValue tokenKeyValue = apiKeyManager.getToken(new TokenManagerKey(USERNAME, TokenType.APIKEY));
        assertNull(tokenKeyValue);
        verify(userGroupService, times(1)).getPropsToken(USERNAME, ApiKeyManager.API_KEY);
    }

    @Test
    void refreshToken() {
        when(userGroupService.updatePropsToken(eq(USERNAME), eq(ApiKeyManager.API_KEY), any())).thenReturn(true);
        TokenKeyValue tokenKeyValue = apiKeyManager.refreshToken(USERNAME);
        assertNotEquals(TOKEN, tokenKeyValue.getToken());
        assertEquals(ApiKeyManager.API_KEY, tokenKeyValue.getKey());
        verify(userGroupService, times(1)).updatePropsToken(eq(USERNAME), eq(ApiKeyManager.API_KEY), any());
    }

    @Test
    void refreshTokenReturnNull() {
        when(userGroupService.updatePropsToken(eq(USERNAME), eq(ApiKeyManager.API_KEY), any())).thenReturn(false);
        TokenKeyValue tokenKeyValue = apiKeyManager.refreshToken(USERNAME);
        assertNull(tokenKeyValue);
        verify(userGroupService, times(1)).updatePropsToken(eq(USERNAME), eq(ApiKeyManager.API_KEY), any());
    }

    @Test
    void refreshTokenReturnNullDueToException() {
        doThrow(new RuntimeException("exception")).when(userGroupService).updatePropsToken(any(), any(), any());
        TokenKeyValue tokenKeyValue = apiKeyManager.refreshToken(USERNAME);
        assertNull(tokenKeyValue);
        verify(userGroupService, times(1)).updatePropsToken(eq(USERNAME), eq(ApiKeyManager.API_KEY), any());
    }
}