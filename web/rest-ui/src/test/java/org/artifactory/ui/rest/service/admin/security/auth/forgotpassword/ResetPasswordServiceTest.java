/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.ui.rest.service.admin.security.auth.forgotpassword;

import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.exceptions.PasswordChangeException;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.expectThrows;

/**
 * @author Asaf Zalcman
 */
public class ResetPasswordServiceTest {

    private static final String GEN_PASSWORD_KEY = "gen-password-key";
    private static final String ERROR_MESSAGE = "key is not valid";
    private ResetPasswordService resetPasswordService;
    @Mock
    private UserGroupService userGroupService;
    @Mock
    private SecurityService securityService;
    @Spy
    private ArtifactoryRestResponse restResponse;
    @Mock
    private MutableUserInfo user;

    @Mock
    private UserGroupStoreService userGroupStoreService;
    private AutoCloseable mocks;


    @BeforeMethod
    public void before() {
        mocks = MockitoAnnotations.openMocks(this);
        when(user.getUsername()).thenReturn("test");
        when(user.getGenPasswordKey()).thenReturn(GEN_PASSWORD_KEY);
        when(userGroupService.findUser(user.getUsername(), false)).thenReturn(user);
        resetPasswordService = new ResetPasswordService(userGroupService, securityService);
        resetPasswordService.setUserGroupStoreService(userGroupStoreService);
    }

    @AfterMethod
    public void after() throws Exception {
        verifyNoMoreInteractions(userGroupService, securityService, restResponse, user, userGroupStoreService);
        mocks.close();
    }

    @Test
    public void testResetPasswordSuccess() {
        String newPassword = "abc";

        when(userGroupService.isValidGenPassword(GEN_PASSWORD_KEY)).thenReturn(true);
        resetPasswordService.saveNewPassword(restResponse, GEN_PASSWORD_KEY, "test", newPassword);
        verify(userGroupService, times(1)).findUser(user.getUsername(), false);
        verify(userGroupService, times(1)).isValidGenPassword(GEN_PASSWORD_KEY);
        verify(securityService, times(1)).changePasswordWithoutValidation(user, newPassword);
        verify(userGroupStoreService, times(1)).changeUserGenPassword(user.getUsername(), "");
        verify(restResponse, times(1)).info("Password reset successfully.");
        verify(user, times(1)).getGenPasswordKey();
        verify(user, times(3)).getUsername();
    }

    @Test
    public void testDifferentGenKeyThenUserGenKey() {
        when(restResponse.error(ERROR_MESSAGE)).thenReturn(restResponse);
        resetPasswordService.saveNewPassword(restResponse, "wrong!", user.getUsername(), "ignore");
        verify(userGroupService, times(1)).findUser(user.getUsername(), false);
        verify(restResponse, times(1)).error(ERROR_MESSAGE);
        verify(user, times(1)).getGenPasswordKey();
        verify(user, times(3)).getUsername();
    }

    @Test
    public void testEmptyGenKey() {
        when(restResponse.error(ERROR_MESSAGE)).thenReturn(restResponse);
        resetPasswordService.saveNewPassword(restResponse, null, user.getUsername(), "ignore");
        verify(userGroupService, times(1)).findUser(user.getUsername(), false);
        verify(restResponse, times(1)).error(ERROR_MESSAGE);
        verify(user, times(3)).getUsername();
    }

    @Test
    public void testUserNotFound() {
        when(restResponse.error(ERROR_MESSAGE)).thenReturn(restResponse);
        resetPasswordService.saveNewPassword(restResponse, "any-key", "nonexistent-user", "ignore");
        verify(userGroupService, times(1)).findUser("nonexistent-user", false);
        verify(restResponse, times(1)).error(ERROR_MESSAGE);
        verify(user, times(1)).getUsername();
    }

    @Test
    public void testNullUserName() {
        when(restResponse.error(ERROR_MESSAGE)).thenReturn(restResponse);
        resetPasswordService.saveNewPassword(restResponse, GEN_PASSWORD_KEY, null, "ignore");
        verify(userGroupService, times(1)).findUser(null, false);
        verify(restResponse, times(1)).error(ERROR_MESSAGE);
        verify(user, times(1)).getUsername();
    }

    @Test
    public void testEmptyUserName() {
        when(restResponse.error(ERROR_MESSAGE)).thenReturn(restResponse);
        resetPasswordService.saveNewPassword(restResponse, GEN_PASSWORD_KEY, "", "ignore");
        verify(userGroupService, times(1)).findUser("", false);
        verify(restResponse, times(1)).error(ERROR_MESSAGE);
        verify(user, times(1)).getUsername();
    }

    @Test
    public void testUserWithNullGenPasswordKey() {
        when(restResponse.error(ERROR_MESSAGE)).thenReturn(restResponse);
        when(user.getGenPasswordKey()).thenReturn(null);
        resetPasswordService.saveNewPassword(restResponse, GEN_PASSWORD_KEY, "test", "ignore");
        verify(userGroupService, times(1)).findUser(user.getUsername(), false);
        verify(restResponse, times(1)).error(ERROR_MESSAGE);
        verify(user, times(1)).getGenPasswordKey();
        verify(user, times(2)).getUsername();
    }

    @Test
    public void testPasswordChangeExceptionPropagates() {
        when(userGroupService.isValidGenPassword(GEN_PASSWORD_KEY)).thenReturn(true);
        doThrow(new PasswordChangeException("The specified user is not permitted to reset his password."))
                .when(securityService).changePasswordWithoutValidation(user, "new-password");
        expectThrows(PasswordChangeException.class,
                () -> resetPasswordService.saveNewPassword(restResponse, GEN_PASSWORD_KEY, "test", "new-password"));
        verify(userGroupService, times(1)).findUser(user.getUsername(), false);
        verify(userGroupService, times(1)).isValidGenPassword(GEN_PASSWORD_KEY);
        verify(securityService, times(1)).changePasswordWithoutValidation(user, "new-password");
        verify(user, times(1)).getGenPasswordKey();
        verify(user, times(2)).getUsername();
    }

    @Test
    public void testExpiredGenKey() {
        when(restResponse.error(ERROR_MESSAGE)).thenReturn(restResponse);
        when(userGroupService.isValidGenPassword(GEN_PASSWORD_KEY)).thenReturn(false);
        resetPasswordService.saveNewPassword(restResponse, GEN_PASSWORD_KEY, user.getUsername(), "123");
        verify(userGroupService, times(1)).findUser(user.getUsername(), false);
        verify(userGroupService, times(1)).isValidGenPassword(GEN_PASSWORD_KEY);
        verify(restResponse, times(1)).error(ERROR_MESSAGE);
        verify(user, times(1)).getGenPasswordKey();
        verify(user, times(3)).getUsername();
    }
}