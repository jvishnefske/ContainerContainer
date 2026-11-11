package org.artifactory.ui.rest.service.admin.security.user.userprofile.ssh;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.AccessLogger;
import org.artifactory.security.UserInfo;
import org.artifactory.security.ssh.PublicKeyAuthenticator;
import org.artifactory.ui.rest.model.admin.configuration.ssh.SshClientUIModel;
import org.artifactory.ui.rest.model.admin.security.user.User;
import org.artifactory.ui.rest.model.admin.security.user.UserProfile;
import org.artifactory.util.CollectionUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @author Natalie Berni
 */
@Slf4j
@Component
public class DeleteUserSSHKeyService extends BaseSshKeyService {

    @Override
    public void execute(ArtifactoryRestRequest<UserProfile> request, RestResponse response) {
        UserProfile profile = request.getImodel();
        deleteSshKeyToUser(response, profile);
    }

    @Override
    protected boolean validateProfileAdditionalInformation(RestResponse artifactoryResponse, UserProfile profile,
            SshClientUIModel sshToDelete, UserInfo userInfo) {
        if (sshToDelete == null || !StringUtils.hasText(sshToDelete.getPublicKey()) ||
                !StringUtils.hasText(sshToDelete.getPublicKeyName())) {
            artifactoryResponse.error("SSH public key name/content are empty");
            artifactoryResponse.responseCode(HttpStatus.SC_FORBIDDEN);
            return false;
        }
        if (CollectionUtils.isNullOrEmpty(getUserSshKeys(userInfo))) {
            artifactoryResponse.error("The user '" + profile.getUser().getName() + "' doesn't have SSH public keys.");
            artifactoryResponse.responseCode(HttpStatus.SC_BAD_REQUEST);
            return false;
        }
        return true;
    }

    private void deleteSshKeyToUser(RestResponse artifactoryResponse, UserProfile profile) {
        SshClientUIModel newSsh = profile.getSsh();
        UserInfo userInfo = userProfileHelperService.loadUserInfo();
        User user = profile.getUser();
        if (assertUserCanUpdateSsh(artifactoryResponse, profile, newSsh, userInfo, user)) {
            deleteUserProfileSshKey(artifactoryResponse, userInfo, newSsh);
        }
    }

    /**
     * Delete old public SSH key to user profile and the last time it was in use.
     *
     * @param artifactoryResponse - encapsulate data require for response
     * @param userInfo            - current logged user info
     * @param ssh                 - Existing public ssh to delete from user property
     */
    private void deleteUserProfileSshKey(RestResponse artifactoryResponse, UserInfo userInfo, SshClientUIModel ssh) {
        if (userGroupService.getUserProperty(userInfo.getUsername(), ssh.getPublicKeyName()) == null) {
            AccessLogger.updated("Could not find and delete SSH key property " + ssh.getPublicKeyName() +
                    " from user: '" + userInfo.getUsername() + "'");
            artifactoryResponse.info("Unable to delete " + ssh.getPublicKeyName() + " SSH key from '"
                    + userInfo.getUsername() + "' profile - property not found");
            artifactoryResponse.responseCode(HttpStatus.SC_NOT_FOUND);
        } else {
            userGroupService.deleteUserProperty(userInfo.getUsername(), ssh.getPublicKeyName());
            userGroupService.deleteUserProperty(userInfo.getUsername(),
                    ssh.getPublicKeyName() + PublicKeyAuthenticator.SSH_DATE_POSTFIX);
            AccessLogger.updated("The user: '" + userInfo.getUsername() + "' has deleted "
                    + ssh.getPublicKeyName() + " SSH key successfully");
            artifactoryResponse.info("Successfully deleted " + ssh.getPublicKeyName() + " SSH key from '"
                    + userInfo.getUsername() + "'  profile");
            artifactoryResponse.responseCode(HttpStatus.SC_OK);
        }
    }
}
