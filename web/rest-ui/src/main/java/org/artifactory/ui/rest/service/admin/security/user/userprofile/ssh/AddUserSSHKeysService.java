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

import java.util.Objects;
import java.util.Set;

/**
 * @author Natalie Berni
 */
@Slf4j
@Component
public class AddUserSSHKeysService extends BaseSshKeyService {

    @Override
    public void execute(ArtifactoryRestRequest<UserProfile> request, RestResponse response) {
        UserProfile profile = request.getImodel();
        addSshKeyToUser(response, profile);
    }

    @Override
    protected boolean validateProfileAdditionalInformation(RestResponse artifactoryResponse, UserProfile profile,
            SshClientUIModel newSshKey, UserInfo userInfo) {
        Set<SshClientUIModel> userSshKeys = getUserSshKeys(userInfo);
        if (newSshKey == null ||
                !StringUtils.hasText(newSshKey.getPublicKey()) || !StringUtils.hasText(newSshKey.getPublicKeyName())) {
            artifactoryResponse.error("SSH public key name/content are empty");
            artifactoryResponse.responseCode(HttpStatus.SC_FORBIDDEN);
            return false;
        }
        if (CollectionUtils.notNullOrEmpty(userSshKeys)) {
            if (userSshKeys.stream().anyMatch(existsSshKey -> existsSshKey.getPublicKeyName()
                    .equals(newSshKey.getPublicKeyName() + PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX))){
                artifactoryResponse.error("SSH public key name " + newSshKey.getPublicKeyName() +
                        PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX + " is already in used");
                artifactoryResponse.responseCode(HttpStatus.SC_BAD_REQUEST);
                return false;
            }
            if (userSshKeys.stream()
                    .map(SshClientUIModel::getPublicKey)
                    .filter(Objects::nonNull)
                    .anyMatch(existsSshKey -> existsSshKey.equals(newSshKey.getPublicKey()))){
                artifactoryResponse.error("SSH public key content " + newSshKey.getPublicKey() + " is already in used");
                artifactoryResponse.responseCode(HttpStatus.SC_BAD_REQUEST);
                return false;
            }
        }
        return true;
    }

    private void addSshKeyToUser(RestResponse<UserProfile> artifactoryResponse, UserProfile profile) {
        SshClientUIModel newSsh = profile.getSsh();
        UserInfo userInfo = userProfileHelperService.loadUserInfo();
        User user = profile.getUser();
        if (assertUserCanUpdateSsh(artifactoryResponse, profile, newSsh, userInfo, user)) {
            addUserProfileSshKey(artifactoryResponse, userInfo, newSsh);
        }
    }

    /**
     * Add new public SSH key to user profile
     *
     * @param artifactoryResponse - encapsulate data require for response
     * @param userInfo            - current logged user info
     * @param ssh                 - new public ssh to add
     */
    private void addUserProfileSshKey(RestResponse artifactoryResponse, UserInfo userInfo, SshClientUIModel ssh) {
        String publicKey = ssh.getPublicKey();
        String[] keyTokens = publicKey.split("\\s");
        String propertyName = ssh.getPublicKeyName() + PublicKeyAuthenticator.SSH_PUBLIC_KEY_POSTFIX;
        String sshHostkeyAlgorithm = keyTokens[0];
        String sshKeyContent = keyTokens.length >= 2 ? " " + keyTokens[1] : "";
        userGroupService.addUserProperty(userInfo.getUsername(), propertyName, sshHostkeyAlgorithm + sshKeyContent);
        userGroupService.addUserProperty(userInfo.getUsername(),
                ssh.getPublicKeyName() + PublicKeyAuthenticator.SSH_PUBLIC_KEY_DATE_POSTFIX,
                String.valueOf(System.currentTimeMillis()));

        AccessLogger.updated("The user: '" + userInfo.getUsername() + "' has added a new SSH key successfully");
        artifactoryResponse.info("Successfully added new SSH key to '" + userInfo.getUsername() + "'  profile");
        artifactoryResponse.responseCode(HttpStatus.SC_OK);
    }
}
