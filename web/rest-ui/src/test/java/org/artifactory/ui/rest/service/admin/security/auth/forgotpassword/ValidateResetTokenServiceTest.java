package org.artifactory.ui.rest.service.admin.security.auth.forgotpassword;

import org.apache.http.HttpStatus;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.UserInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ValidateResetTokenServiceTest {

    private static final String GEN_PASSWORD_KEY = "gen-password-key";
    private static final String ERROR_MESSAGE = "The reset link is invalid.";
    private static final int ERROR_CODE = HttpStatus.SC_UNAUTHORIZED;

    private ValidateResetTokenService validateResetTokenService;
    @Mock
    private UserGroupService userGroupService;
    @Spy
    private ArtifactoryRestResponse restResponse;
    @Mock
    private MutableUserInfo user;
    private AutoCloseable mocks;

    @BeforeEach
    void before() {
        mocks = MockitoAnnotations.openMocks(this);
        when(user.getUsername()).thenReturn("test");
        when(user.getGenPasswordKey()).thenReturn(GEN_PASSWORD_KEY);
        when(userGroupService.getAllUsers(true)).thenReturn(List.of(user));
        validateResetTokenService = new ValidateResetTokenService();
        validateResetTokenService.setUserGroupService(userGroupService);
    }

    @AfterEach
    void tearDown() throws Exception {
        Mockito.verifyNoMoreInteractions(userGroupService, user, restResponse);
        mocks.close();
    }

    @Test
    void testValidateToken() {
        when(userGroupService.isValidGenPassword(GEN_PASSWORD_KEY)).thenReturn(true);
        UserInfo userInfo = validateResetTokenService.findUserByKey(GEN_PASSWORD_KEY, restResponse);
        Assertions.assertEquals(user, userInfo);
        verify(userGroupService, times(1)).isValidGenPassword(GEN_PASSWORD_KEY);
        verify(userGroupService, times(1)).getAllUsers(true);
        verify(user, times(1)).getGenPasswordKey();
    }

    @Test
    void testValidateTokenWithNonExistToken() {
        String givenGeneratedPassword = "notExists";
        when(userGroupService.isValidGenPassword(givenGeneratedPassword)).thenReturn(true);
        UserInfo userInfo = validateResetTokenService.findUserByKey(givenGeneratedPassword, restResponse);
        Assertions.assertNull(userInfo);
        verify(userGroupService, times(1)).isValidGenPassword(givenGeneratedPassword);
        verify(userGroupService, times(1)).getAllUsers(true);
        verify(user, times(1)).getGenPasswordKey();
        verify(restResponse, times(1)).error(ERROR_MESSAGE);
        verify(restResponse, times(1)).responseCode(ERROR_CODE);
    }

    @Test
    void testValidateTokenWithExpiredToken() {
        when(userGroupService.isValidGenPassword(GEN_PASSWORD_KEY)).thenReturn(false);
        UserInfo userInfo = validateResetTokenService.findUserByKey(GEN_PASSWORD_KEY, restResponse);
        Assertions.assertNull(userInfo);
        verify(userGroupService, times(1)).isValidGenPassword(GEN_PASSWORD_KEY);
        verify(restResponse, times(1)).error(ERROR_MESSAGE);
        verify(restResponse, times(1)).responseCode(ERROR_CODE);
    }
}