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

package org.artifactory.ui.rest.service.admin.security.user.userprofile;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.AccessLogger;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.ui.rest.model.admin.configuration.ssh.SshClientUIModel;
import org.artifactory.ui.rest.model.admin.security.user.User;
import org.artifactory.ui.rest.model.admin.security.user.UserProfile;
import org.artifactory.ui.rest.service.admin.security.user.userprofile.ssh.BaseSshKeyService;
import org.artifactory.util.CollectionUtils;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.model.MessageModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @author Chen Keinan
 */
@Slf4j
@Component
public class UpdateUserProfileService extends BaseSshKeyService {

    @Override
    public void execute(ArtifactoryRestRequest<UserProfile> request, RestResponse response) {
        UserProfile profile = request.getImodel();
        // update user profile
        updateUserInfo(response, profile);
    }

    @Override
    protected boolean validateProfileAdditionalInformation(RestResponse artifactoryResponse, UserProfile profile,
            SshClientUIModel sshClientUIModel, UserInfo userInfo) {
        if (!StringUtils.hasText(profile.getUser().getEmail()) && userInfo.getRealm().equals("internal")) {
            artifactoryResponse.error("Email address is required");
            return false;
        }
        return true;
    }

    private void updateUserInfo(RestResponse artifactoryResponse, UserProfile profile) {
        User user = profile.getUser();
        SshClientUIModel ssh = profile.getSsh();
        UserInfo userInfo = userProfileHelperService.loadUserInfo();
        if (assertUserCanUpdateSsh(artifactoryResponse, profile, ssh, userInfo, user)) {
            updateUserProfileToDB(artifactoryResponse, profile, user, ssh, userInfo);
        }
    }

    /**
     * update user profile to DB
     *
     * @param artifactoryResponse - encapsulate data require for response
     * @param profile             - update user profile
     * @param user                - user part from user profile
     * @param userInfo            - current logged user info
     */
    private void updateUserProfileToDB(RestResponse artifactoryResponse, UserProfile profile, User user,
            SshClientUIModel ssh, UserInfo userInfo) {
        MutableUserInfo mutableUser = InfoFactoryHolder.get().copyUser(userInfo);
        mutableUser.setEmail(profile.getUser().getEmail());

        if (!securityService.isDisableInternalPassword()) {
            String newPassword = user.getPassword();
            if (StringUtils.hasText(newPassword)) {
                mutableUser.setPassword(securityService.generateSaltedPassword(newPassword));
            }
        }
        try {
            userGroupService.updateUser(mutableUser, !userInfo.hasSameAuthorizationContext(userInfo));
        } catch (AccessClientHttpException e) {
            List<MessageModel> errors = e.getErrorsModel().getErrors();
            if (CollectionUtils.notNullOrEmpty(errors)) {
                String error = errors.get(0).getMessage();
                log.error("Unable to update user: '{}': {}", userInfo.getUsername(), error);
                throw new IllegalArgumentException(error, e);
            }
            log.error("Unable to update user: '{}'", userInfo.getUsername(), e);
        }

        if (!ConstantValues.sshUserMultiSupportEnabled.getBoolean()) {
            userGroupService.deleteUserProperty(userInfo.getUsername(), "sshPublicKey");
            if ((ssh != null) && org.apache.commons.lang3.StringUtils.isNotBlank(ssh.getPublicKey())) {
                String publicKey = ssh.getPublicKey();
                String[] keyTokens = publicKey.split("\\s");
                userGroupService.addUserProperty(userInfo.getUsername(), "sshPublicKey",
                        keyTokens[0] + (keyTokens.length >= 2 ? " " + keyTokens[1] : ""));
            }
        }

        AccessLogger.updated("The user: '" + mutableUser.getUsername() + "' has updated his profile successfully");
        artifactoryResponse.info("Successfully updated profile '" + mutableUser.getUsername() + "'");
    }
}
