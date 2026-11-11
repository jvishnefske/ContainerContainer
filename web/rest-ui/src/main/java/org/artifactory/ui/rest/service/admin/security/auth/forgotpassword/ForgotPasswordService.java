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
import org.apache.http.HttpStatus;
import org.artifactory.api.security.ResetPasswordException;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.security.login.UserLogin;
import org.artifactory.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URISyntaxException;

/**
 * @author Chen Keinan
 */
@Component
public class ForgotPasswordService implements RestService {

    private static final Logger log = LoggerFactory.getLogger(ForgotPasswordService.class);

    private static final String ERROR_WHILE_RESETTING_FOR_USER_REQUESTED_FROM_ADDRESS =
            "Error while resetting password for user: '{}', requested from address '{}'. {}";

    private static final String ERROR_WHILE_RESETTING_FOR_USER_REQUESTED_TO_INVALID_X_FORWARDED_FOR =
            "Error while resetting password for user: '{}', requested to forwarded for invalid address. {}";

    @Autowired
    UserGroupService userGroupService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        UserLogin userLogin = (UserLogin) request.getImodel();
        String username = userLogin.getUser();
        //Check if username is valid
        if (StringUtils.isEmpty(username)) {
            response.error("User is required");
            response.responseCode(400);
            return;
        }
        // rest user password
        resetPassword(request, response, username);
    }

    /**
     * reset user password
     *
     * @param artifactoryRequest  - encapsulate data require for request
     * @param artifactoryResponse - encapsulate data related to response
     * @param username            - username
     */
    private void resetPassword(ArtifactoryRestRequest artifactoryRequest,
            RestResponse artifactoryResponse,
            String username) {
        // if in aol mode then have to go to the dashboard to reset password
        String remoteAddress = "";
        try {
            remoteAddress = HttpUtils.getRemoteClientAddress(artifactoryRequest.getServletRequest());
            String status = userGroupService.resetPassword(username, remoteAddress);
            artifactoryResponse.info(status);
        } catch (URISyntaxException e) {
            log.error(ERROR_WHILE_RESETTING_FOR_USER_REQUESTED_TO_INVALID_X_FORWARDED_FOR, username, e);
            artifactoryResponse.error(e.getMessage());
            artifactoryResponse.responseCode(HttpStatus.SC_FORBIDDEN);
        } catch (ResetPasswordException e) {
            log.warn(ERROR_WHILE_RESETTING_FOR_USER_REQUESTED_FROM_ADDRESS, username,
                    remoteAddress, e.getMessage());
            log.debug(ERROR_WHILE_RESETTING_FOR_USER_REQUESTED_FROM_ADDRESS, username,
                    remoteAddress, e);
            artifactoryResponse.error(e.getMessage());
            artifactoryResponse.responseCode(HttpStatus.SC_FORBIDDEN);
        } catch (Exception e) {
            log.error(ERROR_WHILE_RESETTING_FOR_USER_REQUESTED_FROM_ADDRESS, username,
                    remoteAddress, e);
            artifactoryResponse.error(e.getMessage());
        }
    }
}
