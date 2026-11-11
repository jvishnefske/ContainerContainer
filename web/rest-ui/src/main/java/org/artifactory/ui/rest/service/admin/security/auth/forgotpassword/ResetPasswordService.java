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
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.UserInfo;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.ui.rest.model.admin.security.login.UserLogin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Chen Keinan
 */
@Component
public class ResetPasswordService<T extends UserLogin> implements RestService<T> {
    private static final Logger log = LoggerFactory.getLogger(ResetPasswordService.class);

    private final UserGroupService userGroupService;
    private final SecurityService securityService;
    private UserGroupStoreService userGroupStoreService;

    @Autowired
    public ResetPasswordService(UserGroupService userGroupService, SecurityService securityService) {
        this.userGroupService = userGroupService;
        this.securityService = securityService;
    }

    @Autowired
    public void setUserGroupStoreService(UserGroupStoreService userGroupStoreService) {
        this.userGroupStoreService = userGroupStoreService;
    }

    @Override
    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {
        String passwordGenKey = request.getQueryParamByKey("key");
        String userName = request.getImodel().getUser();
        String newPassword = request.getImodel().getPassword();
        // save new generated password
        saveNewPassword(response, passwordGenKey, userName, newPassword);
    }

    /**
     * validate generated key and save user new password
     *
     * @param artifactoryResponse - encapsulate data require for response
     * @param passwordGenKey      - reset password generated key
     * @param userName            - user name  which run reset password
     * @param newPassword         - new updated password
     */
    void saveNewPassword(RestResponse artifactoryResponse, String passwordGenKey, String userName, String newPassword) {
        UserInfo user = userGroupService.findUser(userName, false);
        // perform key validation before saving new password
        boolean isKeyValid = validateKey(artifactoryResponse, passwordGenKey, user);
        if (!isKeyValid) {
            return;
        }

        securityService.changePasswordWithoutValidation(user, newPassword);
        userGroupStoreService.changeUserGenPassword(userName, "");
        log.info("The user: '{}' has successfully reset his password.", userName);
        artifactoryResponse.info("Password reset successfully.");
    }

    /**
     * validate generated key before saving password
     *
     * @param artifactoryResponse - encapsulate data require for response
     * @param passwordGenKey      - generated key
     * @param user                - user which run user password
     */
    private boolean validateKey(RestResponse artifactoryResponse, String passwordGenKey, UserInfo user) {
        if (user == null || StringUtils.isEmpty(passwordGenKey) || !passwordGenKey.equals(user.getGenPasswordKey()) ||
                !userGroupService.isValidGenPassword(passwordGenKey)) {
            artifactoryResponse.error("key is not valid");
            return false;
        }
        return true;
    }
}
