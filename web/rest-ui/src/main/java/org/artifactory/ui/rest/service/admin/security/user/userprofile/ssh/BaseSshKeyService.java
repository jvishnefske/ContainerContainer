package org.artifactory.ui.rest.service.admin.security.user.userprofile.ssh;

import org.apache.http.HttpStatus;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.md.Properties;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.UserInfo;
import org.artifactory.security.ssh.PublicKeyAuthenticator;
import org.artifactory.ui.rest.model.admin.configuration.ssh.SshClientUIModel;
import org.artifactory.ui.rest.model.admin.security.user.User;
import org.artifactory.ui.rest.model.admin.security.user.UserProfile;
import org.artifactory.ui.rest.service.admin.security.user.userprofile.UserProfileHelperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;

/***
 *  @author Natalie Berni
 */
public abstract class BaseSshKeyService implements RestService<UserProfile> {
    protected UserGroupService userGroupService;
    protected SecurityService securityService;
    protected UserProfileHelperService userProfileHelperService;

    @Autowired
    public void setUserGroupService(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setUserProfileHelperService(UserProfileHelperService userProfileHelperService) {
        this.userProfileHelperService = userProfileHelperService;
    }

    protected Set<SshClientUIModel> getUserSshKeys(UserInfo userInfo){
        Properties propertiesForUser = userGroupService.findPropertiesForUser(userInfo.getUsername());
        return propertiesForUser.entries().stream()
                .filter(entry ->  entry.getKey().endsWith(PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX))
                .map(entry -> new SshClientUIModel(entry.getKey(), entry.getValue()))
                .collect(Collectors.toSet());
    }

    public final boolean assertUserCanUpdateSsh(RestResponse artifactoryResponse, UserProfile profile,
            SshClientUIModel ssh, UserInfo userInfo, User user) {
        if (user == null){
            artifactoryResponse.error("User cannot be null");
            artifactoryResponse.responseCode(HttpStatus.SC_BAD_REQUEST);
            return false;
        }
        if (userInfo.isAnonymous()) {
            artifactoryResponse.error("Unable to edit settings for anonymous user");
            artifactoryResponse.responseCode(HttpStatus.SC_FORBIDDEN);
            return false;
        }
        if (!securityService.isProfileUpdatable(userInfo)) {
            artifactoryResponse.error("User not allowed to update profile");
            artifactoryResponse.responseCode(HttpStatus.SC_FORBIDDEN);
            return false;
        }
        if (userInfo.isPasswordDisabled() && StringUtils.hasText(user.getPassword())) {
            artifactoryResponse.error("User not allowed to set password");
            artifactoryResponse.responseCode(HttpStatus.SC_FORBIDDEN);
            return false;
        }
        return validateProfileAdditionalInformation(artifactoryResponse, profile, ssh, userInfo);
    }

    protected abstract boolean validateProfileAdditionalInformation(RestResponse artifactoryResponse,
            UserProfile profile, SshClientUIModel sshClientUIModel, UserInfo userInfo);

}