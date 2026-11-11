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

package org.artifactory.ui.rest.service.admin.security;

import org.artifactory.descriptor.security.UserLockPolicy;
import org.artifactory.rest.common.dataholder.PasswordContainer;
import org.artifactory.rest.common.service.admin.userprofile.CreateApiKeyService;
import org.artifactory.rest.common.service.admin.userprofile.GetApiKeyService;
import org.artifactory.rest.common.service.admin.userprofile.RevokeApiKeyService;
import org.artifactory.rest.common.service.admin.userprofile.UpdateApiKeyService;
import org.artifactory.ui.rest.model.admin.security.oauth.OAuthUserToken;
import org.artifactory.ui.rest.model.admin.security.user.User;
import org.artifactory.ui.rest.service.admin.security.accesstokens.GetAccessTokensService;
import org.artifactory.ui.rest.service.admin.security.accesstokens.RevokeAccessTokensService;
import org.artifactory.ui.rest.service.admin.security.auth.annotate.GetCanAnnotateService;
import org.artifactory.ui.rest.service.admin.security.auth.authentication.GetAuthenticationStatusService;
import org.artifactory.ui.rest.service.admin.security.auth.currentuser.GetCurrentUserService;
import org.artifactory.ui.rest.service.admin.security.auth.forgotpassword.ForgotPasswordService;
import org.artifactory.ui.rest.service.admin.security.auth.forgotpassword.LoginRelatedDataService;
import org.artifactory.ui.rest.service.admin.security.auth.forgotpassword.ResetPasswordService;
import org.artifactory.ui.rest.service.admin.security.auth.forgotpassword.ValidateResetTokenService;
import org.artifactory.ui.rest.service.admin.security.auth.login.LoginService;
import org.artifactory.ui.rest.service.admin.security.auth.logout.LogoutService;
import org.artifactory.ui.rest.service.admin.security.crowdsso.CrowdSsoLoginService;
import org.artifactory.ui.rest.service.admin.security.crowdsso.GetCrowdIntegrationService;
import org.artifactory.ui.rest.service.admin.security.crowdsso.ImportCrowdGroupsService;
import org.artifactory.ui.rest.service.admin.security.crowdsso.RefreshCrowdGroupsService;
import org.artifactory.ui.rest.service.admin.security.crowdsso.TestCrowdConnectionService;
import org.artifactory.ui.rest.service.admin.security.crowdsso.UpdateCrowdIntegration;
import org.artifactory.ui.rest.service.admin.security.general.EncryptDecryptService;
import org.artifactory.ui.rest.service.admin.security.general.GetAllLockedUsersService;
import org.artifactory.ui.rest.service.admin.security.general.GetArtifactoryKeyService;
import org.artifactory.ui.rest.service.admin.security.general.GetSecurityConfigService;
import org.artifactory.ui.rest.service.admin.security.general.GetUserLockPolicyService;
import org.artifactory.ui.rest.service.admin.security.general.UnlockAllUsersService;
import org.artifactory.ui.rest.service.admin.security.general.UnlockUserService;
import org.artifactory.ui.rest.service.admin.security.general.UnlockUsersService;
import org.artifactory.ui.rest.service.admin.security.general.UpdateSecurityConfigService;
import org.artifactory.ui.rest.service.admin.security.group.GetAllGroupNamesService;
import org.artifactory.ui.rest.service.admin.security.httpsso.GetHttpSsoService;
import org.artifactory.ui.rest.service.admin.security.httpsso.UpdateHttpSsoService;
import org.artifactory.ui.rest.service.admin.security.ldap.groups.CreateLdapGroupService;
import org.artifactory.ui.rest.service.admin.security.ldap.groups.DeleteLdapGroupService;
import org.artifactory.ui.rest.service.admin.security.ldap.groups.GetLdapGroupService;
import org.artifactory.ui.rest.service.admin.security.ldap.groups.GroupMappingStrategyService;
import org.artifactory.ui.rest.service.admin.security.ldap.groups.ImportLdapGroupService;
import org.artifactory.ui.rest.service.admin.security.ldap.groups.RefreshLdapGroupService;
import org.artifactory.ui.rest.service.admin.security.ldap.groups.UpdateLdapGroupService;
import org.artifactory.ui.rest.service.admin.security.ldap.ldapsettings.CreateLdapSettingsService;
import org.artifactory.ui.rest.service.admin.security.ldap.ldapsettings.DeleteLdapSettingsService;
import org.artifactory.ui.rest.service.admin.security.ldap.ldapsettings.GetLdapSettingsService;
import org.artifactory.ui.rest.service.admin.security.ldap.ldapsettings.ReorderLdapSettingsService;
import org.artifactory.ui.rest.service.admin.security.ldap.ldapsettings.TestLdapSettingsService;
import org.artifactory.ui.rest.service.admin.security.ldap.ldapsettings.UpdateLdapSettingsService;
import org.artifactory.ui.rest.service.admin.security.oauth.AddOAuthProviderSettings;
import org.artifactory.ui.rest.service.admin.security.oauth.DeleteOAuthProviderSettings;
import org.artifactory.ui.rest.service.admin.security.oauth.DeleteOAuthUserToken;
import org.artifactory.ui.rest.service.admin.security.oauth.GetCentralOAuthSettings;
import org.artifactory.ui.rest.service.admin.security.oauth.GetOAuthSettings;
import org.artifactory.ui.rest.service.admin.security.oauth.GetOAuthTokensForUser;
import org.artifactory.ui.rest.service.admin.security.oauth.UpdateOAuthProviderSettings;
import org.artifactory.ui.rest.service.admin.security.oauth.UpdateOrCreateOAuthSettings;
import org.artifactory.ui.rest.service.admin.security.openid.RedirectService;
import org.artifactory.ui.rest.service.admin.security.permissions.CreatePermissionsTargetService;
import org.artifactory.ui.rest.service.admin.security.permissions.DeletePermissionsTargetService;
import org.artifactory.ui.rest.service.admin.security.permissions.GetAllUsersAndGroupsService;
import org.artifactory.ui.rest.service.admin.security.permissions.GetBuildGlobalBasicReadAllowedService;
import org.artifactory.ui.rest.service.admin.security.permissions.GetBuildPermissionsTargetBuildsService;
import org.artifactory.ui.rest.service.admin.security.permissions.GetPermissionTargetGroupsService;
import org.artifactory.ui.rest.service.admin.security.permissions.GetPermissionTargetResourcesService;
import org.artifactory.ui.rest.service.admin.security.permissions.GetPermissionTargetUsersService;
import org.artifactory.ui.rest.service.admin.security.permissions.GetPermissionsTargetService;
import org.artifactory.ui.rest.service.admin.security.permissions.GetReleaseBundlesByReposAndPatternsService;
import org.artifactory.ui.rest.service.admin.security.permissions.UpdatePermissionsTargetService;
import org.artifactory.ui.rest.service.admin.security.saml.GetSAMLPublicCertificateForEncryptionService;
import org.artifactory.ui.rest.service.admin.security.saml.GetSamlLoginRequestService;
import org.artifactory.ui.rest.service.admin.security.saml.GetSamlLoginResponseService;
import org.artifactory.ui.rest.service.admin.security.saml.GetSamlLogoutRequestService;
import org.artifactory.ui.rest.service.admin.security.saml.GetSamlService;
import org.artifactory.ui.rest.service.admin.security.saml.IsSamlAuthentication;
import org.artifactory.ui.rest.service.admin.security.saml.RegenerateSAMLPublicCertificateForEncryptionService;
import org.artifactory.ui.rest.service.admin.security.saml.UpdateSamlService;
import org.artifactory.ui.rest.service.admin.security.signingkeys.GetSigningKeyService;
import org.artifactory.ui.rest.service.admin.security.signingkeys.InstallSigningKeyService;
import org.artifactory.ui.rest.service.admin.security.signingkeys.RemoveSigningKeyService;
import org.artifactory.ui.rest.service.admin.security.signingkeys.UpdateSigningKeyService;
import org.artifactory.ui.rest.service.admin.security.signingkeys.VerifySigningKeyService;
import org.artifactory.ui.rest.service.admin.security.signingkeys.keystore.AddKeyStoreService;
import org.artifactory.ui.rest.service.admin.security.signingkeys.keystore.CancelKeyPairService;
import org.artifactory.ui.rest.service.admin.security.signingkeys.keystore.ChangeKeyStorePasswordService;
import org.artifactory.ui.rest.service.admin.security.signingkeys.keystore.GetKeyStoreService;
import org.artifactory.ui.rest.service.admin.security.signingkeys.keystore.RemoveKeyStorePasswordService;
import org.artifactory.ui.rest.service.admin.security.signingkeys.keystore.RemoveKeyStoreService;
import org.artifactory.ui.rest.service.admin.security.signingkeys.keystore.SaveKeyStoreService;
import org.artifactory.ui.rest.service.admin.security.sshserver.GetSshServerKeyService;
import org.artifactory.ui.rest.service.admin.security.sshserver.GetSshServerService;
import org.artifactory.ui.rest.service.admin.security.sshserver.InstallSshServerKeyService;
import org.artifactory.ui.rest.service.admin.security.sshserver.RemoveSshServerKeyService;
import org.artifactory.ui.rest.service.admin.security.sshserver.UpdateSshServerService;
import org.artifactory.ui.rest.service.admin.security.ssl.AddCertificateService;
import org.artifactory.ui.rest.service.admin.security.ssl.GetAllCertificateDataService;
import org.artifactory.ui.rest.service.admin.security.ssl.GetCertificateDetails;
import org.artifactory.ui.rest.service.admin.security.ssl.RemoveCertificateService;
import org.artifactory.ui.rest.service.admin.security.user.ChangePasswordService;
import org.artifactory.ui.rest.service.admin.security.user.CheckExternalStatusService;
import org.artifactory.ui.rest.service.admin.security.user.CreateUserService;
import org.artifactory.ui.rest.service.admin.security.user.DeleteUserService;
import org.artifactory.ui.rest.service.admin.security.user.ExpirePasswordForAllUsersService;
import org.artifactory.ui.rest.service.admin.security.user.ExpireUserPasswordService;
import org.artifactory.ui.rest.service.admin.security.user.GetAllUsersService;
import org.artifactory.ui.rest.service.admin.security.user.GetUserService;
import org.artifactory.ui.rest.service.admin.security.user.RevalidatePasswordForAllUsersService;
import org.artifactory.ui.rest.service.admin.security.user.RevalidatePasswordService;
import org.artifactory.ui.rest.service.admin.security.user.UpdateUserLockPolicyService;
import org.artifactory.ui.rest.service.admin.security.user.UpdateUserService;
import org.artifactory.ui.rest.service.admin.security.user.userprofile.UnlockUserProfileService;
import org.artifactory.ui.rest.service.admin.security.user.userprofile.UpdateUserProfileService;
import org.artifactory.ui.rest.service.admin.security.user.userprofile.ssh.AddUserSSHKeysService;
import org.artifactory.ui.rest.service.admin.security.user.userprofile.ssh.DeleteUserSSHKeyService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.permission.GetRepoEffectivePermissionsByEntityService;
import org.springframework.beans.factory.annotation.Lookup;

import java.util.List;

/**
 * @author Chen Keinan
 */
public abstract class SecurityServiceFactory {

    //authentication service
    @Lookup
    public abstract LoginService loginService();

    @Lookup
    public abstract ForgotPasswordService forgotPassword();

    @Lookup
    public abstract IsSamlAuthentication isSamlAuthentication();

    @Lookup
    public abstract RedirectService redirectService();

    @Lookup
    public abstract GetOAuthSettings getOAuthtSettings();

    @Lookup
    public abstract UpdateOrCreateOAuthSettings updateOAuthSettings();

    @Lookup
    public abstract AddOAuthProviderSettings addOAuthProviderSettings();

    @Lookup
    public abstract UpdateOAuthProviderSettings updateOAuthProviderSettings();

    @Lookup
    public abstract DeleteOAuthProviderSettings deleteOAuthProviderSettings();

    @Lookup
    public abstract GetCentralOAuthSettings getCentralOAuthSettings();

    @Lookup
    public abstract GetOAuthTokensForUser getOAuthTokensForUser();

    @Lookup
    public abstract DeleteOAuthUserToken<OAuthUserToken> deleteOAuthUserToken();

    @Lookup
    public abstract ValidateResetTokenService validateToken();

    @Lookup
    public abstract LoginRelatedDataService loginRelatedData();

    @Lookup
    public abstract GetCurrentUserService getCurrentUser();

    @Lookup
    public abstract GetAuthenticationStatusService getAuthenticationStatusService();

    @Lookup
    public abstract GetCanAnnotateService getCanAnnotateService();

    @Lookup
    public abstract ResetPasswordService resetPassword();

    @Lookup
    public abstract LogoutService logoutService();

    // user services
    @Lookup
    public abstract CreateUserService<User> createUser();

    @Lookup
    public abstract ChangePasswordService<PasswordContainer> changePassword();

    @Lookup
    public abstract ExpireUserPasswordService<String> expireUserPassword();

    @Lookup
    public abstract RevalidatePasswordService<String> unexpirePassword();

    @Lookup
    public abstract ExpirePasswordForAllUsersService expirePasswordForAllUsers();

    @Lookup
    public abstract RevalidatePasswordForAllUsersService unexpirePasswordForAllUsers();

    @Lookup
    public abstract CheckExternalStatusService<User> checkExternalStatus();

    @Lookup
    public abstract UpdateUserService<User> updateUser();

    @Lookup
    public abstract DeleteUserService deleteUser();

    @Lookup
    public abstract GetAllUsersService getAllUsers();

    @Lookup
    public abstract GetUserService getUser();

    @Lookup
    public abstract GetAllGroupNamesService getAllGroupNames();

    @Lookup
    public abstract UpdateSecurityConfigService updateSecurityConfig();

    @Lookup
    public abstract EncryptDecryptService encryptPassword();

    @Lookup
    public abstract GetSecurityConfigService getSecurityConfig();

    @Lookup
    public abstract UpdateUserLockPolicyService<UserLockPolicy> updateUserLockPolicy();

    @Lookup
    public abstract GetUserLockPolicyService getUserLockPolicy();

    @Lookup
    public abstract UnlockUserService<String> unlockUser();

    @Lookup
    public abstract UnlockUsersService<List> unlockUsers();

    @Lookup
    public abstract UnlockAllUsersService unlockAllUsers();

    @Lookup
    public abstract GetAllLockedUsersService getAllLockedUsers();

    @Lookup
    public abstract GetArtifactoryKeyService getArtifactoryKey();

    @Lookup
    public abstract UpdateHttpSsoService updateHttpSso();

    @Lookup
    public abstract GetHttpSsoService getHttpSso();

    @Lookup
    public abstract UpdateSshServerService updateSshServer();

    @Lookup
    public abstract GetSshServerService getSshServer();

    @Lookup
    public abstract InstallSshServerKeyService uploadSshServerKey();

    @Lookup
    public abstract AddCertificateService addPemClientCertificate();

    @Lookup
    public abstract GetAllCertificateDataService getCertificatesData();

    @Lookup
    public abstract GetCertificateDetails getCertificateDetails();

    @Lookup
    public abstract RemoveCertificateService removeCertificate();

    @Lookup
    public abstract GetSshServerKeyService getSshServerKey();

    @Lookup
    public abstract RemoveSshServerKeyService removeSshServerKeyService();

    @Lookup
    public abstract UpdateSamlService updateSaml();

    @Lookup
    public abstract GetSamlService getSaml();

    @Lookup
    public abstract GetSAMLPublicCertificateForEncryptionService getPublicCertificateForEncryption();

    @Lookup
    public abstract RegenerateSAMLPublicCertificateForEncryptionService regenerateSAMLPublicCertificateForEncryption();

    @Lookup
    public abstract GetSamlLoginRequestService handleLoginRequest();

    @Lookup
    public abstract GetSamlLoginResponseService handleLoginResponse();

    @Lookup
    public abstract GetSamlLogoutRequestService handleLogoutRequest();

    @Lookup
    public abstract GetCrowdIntegrationService getCrowdIntegration();

    @Lookup
    public abstract UpdateCrowdIntegration updateCrowdIntegration();

    @Lookup
    public abstract RefreshCrowdGroupsService refreshCrowdGroups();

    @Lookup
    public abstract ImportCrowdGroupsService importCrowdGroups();

    @Lookup
    public abstract CrowdSsoLoginService crowdSsoLoginService();

    @Lookup
    public abstract TestCrowdConnectionService testCrowdConnectionService();

    @Lookup
    public abstract CreateLdapSettingsService createLdapSettings();

    @Lookup
    public abstract UpdateLdapSettingsService updateLdapSettings();

    @Lookup
    public abstract GetLdapSettingsService getLdapSettings();

    @Lookup
    public abstract DeleteLdapSettingsService deleteLdapSettings();

    @Lookup
    public abstract TestLdapSettingsService testLdapSettingsService();

    @Lookup
    public abstract ReorderLdapSettingsService reorderLdapSettings();

    @Lookup
    public abstract CreateLdapGroupService createLdapGroup();

    @Lookup
    public abstract UpdateLdapGroupService updateLdapGroup();

    @Lookup
    public abstract GetLdapGroupService getLdapGroup();

    @Lookup
    public abstract GroupMappingStrategyService groupMappingStrategy();

    @Lookup
    public abstract DeleteLdapGroupService deleteLdapGroup();

    @Lookup
    public abstract RefreshLdapGroupService refreshLdapGroup();

    @Lookup
    public abstract ImportLdapGroupService importLdapGroup();

    @Lookup
    public abstract UnlockUserProfileService unlockUserProfile();

    @Lookup
    public abstract UpdateUserProfileService updateUserProfile();
    @Lookup
    public abstract DeleteUserSSHKeyService deleteUserSSHKeyService();
    @Lookup
    public abstract AddUserSSHKeysService addUserSSHKeysService();

    @Lookup
    public abstract GetApiKeyService getApiKey();

    @Lookup
    public abstract CreateApiKeyService createApiKey();

    @Lookup
    public abstract RevokeApiKeyService revokeApiKey();

    @Lookup
    public abstract UpdateApiKeyService regenerateApiKey();

    @Lookup
    public abstract InstallSigningKeyService uploadSigningKey();

    @Lookup
    public abstract GetSigningKeyService getSigningKey();

    @Lookup
    public abstract RemoveSigningKeyService removeSigningKeyService();

    @Lookup
    public abstract VerifySigningKeyService verifySigningKey();

    @Lookup
    public abstract UpdateSigningKeyService updateSigningKey();

    @Lookup
    public abstract AddKeyStoreService addKeyStore();

    @Lookup
    public abstract SaveKeyStoreService saveKeyStore();

    @Lookup
    public abstract GetKeyStoreService getKeyStore();

    @Lookup
    public abstract RemoveKeyStorePasswordService removeKeystorePassword();

    @Lookup
    public abstract CancelKeyPairService cancelKeyPair();

    @Lookup
    public abstract RemoveKeyStoreService removeKeyStore();

    @Lookup
    public abstract ChangeKeyStorePasswordService changeKeyStorePassword();

    @Lookup
    public abstract GetPermissionsTargetService getPermissionsTarget();

    @Lookup
    public abstract GetPermissionTargetUsersService getPermissionsTargetUsers();

    @Lookup
    public abstract GetPermissionTargetGroupsService getPermissionsTargetGroups();

    @Lookup
    public abstract GetPermissionTargetResourcesService getPermissionsTargetResources();

    @Lookup
    public abstract GetAllUsersAndGroupsService getAllUsersAndGroups();

    @Lookup
    public abstract GetRepoEffectivePermissionsByEntityService getRepoEffectivePermissionServiceByEntity();

    @Lookup
    public abstract UpdatePermissionsTargetService updatePermissionsTarget();

    @Lookup
    public abstract CreatePermissionsTargetService createPermissionsTarget();

    @Lookup
    public abstract DeletePermissionsTargetService deletePermissionsTarget();

    @Lookup
    public abstract GetBuildPermissionsTargetBuildsService getBuildPermissionsPatterns();

    @Lookup
    public abstract GetReleaseBundlesByReposAndPatternsService getReleaseBundlesByReposAndPatterns();

    @Lookup
    public abstract GetBuildGlobalBasicReadAllowedService getBuildGlobalBasicReadAllowed();

    @Lookup
    public abstract GetAccessTokensService getAccessTokens();

    @Lookup
    public abstract RevokeAccessTokensService revokeAccessToken();
}
