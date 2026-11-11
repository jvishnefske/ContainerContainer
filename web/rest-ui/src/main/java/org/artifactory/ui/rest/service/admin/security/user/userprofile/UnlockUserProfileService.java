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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.security.PasswordEncryptionFailureException;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.md.Properties;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.ssh.PublicKeyAuthenticator;
import org.artifactory.ui.rest.model.admin.configuration.ssh.SshClientUIModel;
import org.artifactory.ui.rest.model.admin.security.login.UserLogin;
import org.artifactory.ui.rest.model.admin.security.user.User;
import org.artifactory.ui.rest.model.admin.security.user.UserProfile;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Chen Keinan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnlockUserProfileService implements RestService {

    private final SecurityService securityService;
    private final UserGroupService userGroupService;
    private final UserProfileHelperService userProfileHelperService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        UserLogin userLogin = (UserLogin) request.getImodel();
        // fetch user profile
        String overrideRealm = request.getQueryParamByKey("realm");
        String providerName = request.getQueryParamByKey("providerName");
        fetchUserProfile(response, userLogin, overrideRealm, providerName);
    }

    /**
     * fetch user profile
     *
     * @param artifactoryResponse - encapsulate data related to response
     * @param userLogin           - hold entered password details
     * @param overrideRealm       - realm to override
     * @param oauthProviderName   - provider name to match against oauth config
     */
    private void fetchUserProfile(RestResponse artifactoryResponse, UserLogin userLogin, @Nullable String overrideRealm,
            @Nullable String oauthProviderName) {
        UserInfo userInfo = userProfileHelperService.loadUserInfo();
        if (userInfo.isAnonymous()) {
            artifactoryResponse.error("Unable to unlock settings for anonymous user");
            return;
        }

        String enteredCurrentPassword = userLogin.getPassword();
        boolean requireProfileUnlock = securityService.requireProfileUnlock(overrideRealm, oauthProviderName);

        if (requireProfileUnlock && !userProfileHelperService.authenticate(userInfo, enteredCurrentPassword)) {
            artifactoryResponse.error("The specified password is incorrect");
            return;
        }
        // get user  profile
        UserProfile userProfile = getUserProfile(userInfo, userLogin);
        artifactoryResponse.iModel(userProfile);
    }

    /**
     * get user profile66
     *
     * @param userInfo - current logged user info
     * @return current user profile
     */
    private UserProfile getUserProfile(UserInfo userInfo, UserLogin userLogin) {
        UserProfile userProfile = new UserProfile();
        updateUserInfo(userInfo, userProfile, userLogin);
        updateSshInfo(userInfo, userProfile);
        updateUserKeysInfo(userInfo, userProfile);
        return userProfile;
    }

    /**
     * update user info in profile
     *
     * @param userInfo    - user info
     * @param userProfile - user profile
     */
    private void updateUserInfo(UserInfo userInfo, UserProfile userProfile, UserLogin userLogin) {
        User user = new User();
        user.setRealm(userInfo.getRealm());
        user.setEmail(userInfo.getEmail());
        user.setProfileUpdatable(userInfo.isUpdatableProfile());
        user.setInternalPasswordDisabled(userInfo.isPasswordDisabled());
        userProfile.setUser(user);
        MutableUserInfo mutableUser = InfoFactoryHolder.get().copyUser(userInfo);
        try {
            String encryptedPassword =
                    userGroupService.createEncryptedPasswordIfNeeded(mutableUser, userLogin.getPassword());
            user.setPassword(encryptedPassword);
        } catch (Exception e) {
            if (e instanceof PasswordEncryptionFailureException) {
                resetUserKeys(mutableUser);

                // Fallback and try to get the encryptedPassword again
                log.trace("Fallback retrieving the encrypted password");
                try {
                    String encryptedPassword =
                            userGroupService.createEncryptedPasswordIfNeeded(mutableUser, userLogin.getPassword());
                    user.setPassword(encryptedPassword);
                } catch (PasswordEncryptionFailureException e1) {
                    resetUserKeys(mutableUser);
                    userGroupService.updateUser(mutableUser, false);
                    user.setPassword(null);
                    throw new IllegalStateException("Fallback password encryption failed", e);
                }
            }
        }
    }

    private void resetUserKeys(MutableUserInfo mutableUser) {
        mutableUser.setPrivateKey(null);
        mutableUser.setPublicKey(null);
    }

    private void updateSshInfo(UserInfo userInfo, UserProfile userProfile) {
        Properties propertiesForUser = userGroupService.findPropertiesForUser(userInfo.getUsername());
        String sshPublicKey = propertiesForUser.getFirst(PublicKeyAuthenticator.SSH_PUBLIC_KEY_PROPERTY);
        SshClientUIModel sshClientUIModel = new SshClientUIModel();
        if (StringUtils.isNotBlank(sshPublicKey)) {
            sshClientUIModel.setPublicKey(sshPublicKey);
        }
        userProfile.setSsh(sshClientUIModel);
    }

    private void updateUserKeysInfo(UserInfo userInfo, UserProfile userProfile) {
        Properties propertiesForUser = userGroupService.findPropertiesForUser(userInfo.getUsername());
        Set<SshClientUIModel> sshPublicKeys = propertiesForUser.entries().stream()
                .filter(entry -> entry.getKey().endsWith(PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX))
                .map(entry -> new SshClientUIModel(entry.getKey(), entry.getValue()))
                .collect(Collectors.toSet());
        log.debug("Found {} SSH public keys for user {}", sshPublicKeys.size(), userInfo.getUsername());
        userProfile.setSshKeys(sshPublicKeys);
    }
}
