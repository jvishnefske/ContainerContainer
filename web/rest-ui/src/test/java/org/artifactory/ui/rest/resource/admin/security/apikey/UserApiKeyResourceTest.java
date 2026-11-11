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

package org.artifactory.ui.rest.resource.admin.security.apikey;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.security.UserInfo;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.artifactory.ui.rest.service.admin.security.SecurityServiceFactory;
import org.artifactory.ui.rest.service.admin.security.user.userprofile.UserProfileHelperService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Bar Haim
 */
public class UserApiKeyResourceTest {

    private static final String ADMIN = "admin";
    private static final String TEST_USER = "test-user";

    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private ApiKeyManager apiKeyManager;
    @Mock
    SecurityServiceFactory securityServiceFactory;
    @Mock
    private SecurityService securityService;
    @Mock
    private UserProfileHelperService userProfileHelperService;
    private AutoCloseable mocks;

    @BeforeMethod
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @DataProvider
    private static Object[][] provideIdAndUserNameForTestIsExist() {
        return new Object[][] {
                {ADMIN, ADMIN, 200},
                {TEST_USER, ADMIN, 200},
                {TEST_USER, TEST_USER, 200},
                {"", TEST_USER, 200},
                {ADMIN, TEST_USER, 400},
                {"not-exists-user", ADMIN, 404}
        };
    }

    /**
     * requireProfileUnlock, expected
     */
    @DataProvider
    private static Object[][] authRequirementProvider() {
        return new Object[][] {
                {true, false},
                {false, true},
        };
    }

    @Test(dataProvider = "authRequirementProvider")
    public void testIsAllowApiKeyAccessUserAuthenticatedNoAuthHeader(boolean requireProfileUnlock, boolean expected) {
        String realm = "realm";
        String providerName = "provider";

        UserInfo userInfo = mock(UserInfo.class);
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        UserApiKeyResource userApiKeyResource = new UserApiKeyResource(securityServiceFactory,
                userProfileHelperService, authorizationService, securityService);
        userApiKeyResource.setHttpServletRequest(httpServletRequest);
        when(securityService.requireProfileUnlock(realm, providerName)).thenReturn(requireProfileUnlock);

        when(httpServletRequest.getHeader(UserProfileHelperService.RE_AUTH_HEADER)).thenReturn(null);
        when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);
        when(userInfo.isAnonymous()).thenReturn(false);
        assertEquals(expected, isBlank(userApiKeyResource.isAllowAccess(realm, providerName)));
    }

    @Test
    public void testIsAllowApiKeyAccessAuthenticateWithPassword() {
        UserInfo userInfo = mock(UserInfo.class);
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(httpServletRequest.getHeader(UserProfileHelperService.RE_AUTH_HEADER))
                .thenReturn("Basic YWRtaW46cGFzc3dvcmQ=");
        UserApiKeyResource userApiKeyResource = new UserApiKeyResource(securityServiceFactory,
                userProfileHelperService, authorizationService, securityService);
        when(userProfileHelperService.authenticate(userInfo, "password")).thenReturn(true);
        userApiKeyResource.setHttpServletRequest(httpServletRequest);
        when(securityService.requireProfileUnlock(anyString(), anyString())).thenReturn(true);
        when(userProfileHelperService.loadUserInfo()).thenReturn(userInfo);
        when(userInfo.getUsername()).thenReturn("admin");
        when(userInfo.isAnonymous()).thenReturn(false);
        assertTrue(isBlank(userApiKeyResource.isAllowAccess(null, null)));
    }

    @Test(dataProvider = "provideIdAndUserNameForTestIsExist")
    public void testIsExist(String id, String userName, int expectedStatusCode) {
        when(apiKeyManager.getToken((TEST_USER))).thenReturn(mock(TokenKeyValue.class));
        when(apiKeyManager.getToken((ADMIN))).thenReturn(mock(TokenKeyValue.class));
        when(securityService.isAnonymous()).thenReturn(false);
        when(securityService.currentUsername()).thenReturn(userName);
        when(securityService.isAdmin()).thenReturn(userName.equals(ADMIN));
        UserApiKeyResource userApiKeyResource = new UserApiKeyResource(securityServiceFactory,
                userProfileHelperService, authorizationService, securityService);
        userApiKeyResource.apiKeyManager = apiKeyManager;
        Response response = userApiKeyResource.isExist(id);
        assertEquals(response.getStatus(), expectedStatusCode);
    }
}