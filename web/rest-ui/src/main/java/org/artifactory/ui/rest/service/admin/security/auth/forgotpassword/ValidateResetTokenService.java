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

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.UserInfo;
import org.artifactory.ui.rest.model.admin.security.login.UserLogin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * @author Chen Keinan
 */
@Component
public class ValidateResetTokenService implements RestService {

    UserGroupService userGroupService;

    @Autowired
    public void setUserGroupService(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String key = request.getQueryParamByKey("key");
        // validate key , return user info id found
        UserInfo userByKey = findUserByKey(key, response);
        updateResponseModel(response, userByKey);
    }

    private void updateResponseModel(RestResponse artifactoryResponse, UserInfo userByKey) {
        if (userByKey != null) {
            UserLogin userLogin = new UserLogin(userByKey.getUsername());
            artifactoryResponse.iModel(userLogin);
        }
    }

    /**
     * Returns a user info object that belongs to the user which is associated with the given key If the user is not
     * found, display a warning and redirect to the login page
     *
     * @param genPasswordKey given genPasswordKey to validate
     * @return UserInfo - UserInfo object of the user that's associated with the given key. Null
     */
    UserInfo findUserByKey(@Nullable String genPasswordKey, RestResponse artifactoryResponse) {
        if (userGroupService.isValidGenPassword(genPasswordKey)) {
            List<UserInfo> userInfoList = userGroupService.getAllUsers(true);
            for (UserInfo userInfo : userInfoList) {
                if (!StringUtils.isEmpty(genPasswordKey) && genPasswordKey.equals(userInfo.getGenPasswordKey())) {
                    return userInfo;
                }
            }
        }
        artifactoryResponse.error("The reset link is invalid.");
        artifactoryResponse.responseCode(HttpServletResponse.SC_UNAUTHORIZED);
        return null;
    }
}
