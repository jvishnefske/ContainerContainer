package org.artifactory.storage.db.itest;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.repo.RepoActionType;
import org.artifactory.api.security.PasswordExpiryUser;
import org.artifactory.api.security.SecurityListener;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.security.SaltedPassword;
import org.artifactory.security.SecurityInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.util.SerializablePair;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * @author Dvir Twina
 */
public class DummySecurityService implements SecurityService {
    @Override
    public SecurityInfo getSecurityData() {
        throw unexpectedMethodCallException();
    }

    @Override
    public SecurityInfo getSecurityData(boolean decryptUserInfoProperties) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void importSecurityData(String securityXml) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void importSecurityData(String securityXml, boolean override) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void importSecurityData(SecurityInfo descriptor) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void decryptAllUserProps() {
        throw unexpectedMethodCallException();
    }

    @Override
    public BasicStatusHolder testLdapConnection(LdapSetting ldapSetting, String username, String password) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isPasswordEncryptionEnabled() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean userPasswordMatches(String passwordToCheck) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void generatePasswordResetKey(String username, String clientIp) throws IOException {
        throw unexpectedMethodCallException();
    }

    @Override
    public SerializablePair<Date, String> getPasswordResetKeyInfo(String username) {
        throw unexpectedMethodCallException();
    }

    @Override
    public SerializablePair<String, Long> getUserLastLoginInfo(String username) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isHasPriorLogin() {
        throw unexpectedMethodCallException();
    }

    @Override
    public void updateUserLastLogin(String username, long loginTimeMillis, String clientIp) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void updateUserLastAccess(String username, String clientIp, long accessTimeMillis) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isHttpSsoProxied() {
        throw unexpectedMethodCallException();
    }

    @Override
    public String getHttpSsoRemoteUserRequestVariable() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isNoHttpSsoAutoUserCreation() {
        throw unexpectedMethodCallException();
    }

    @Override
    public void addListener(SecurityListener listener) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void removeListener(SecurityListener listener) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void authenticateAsSystem() {
        throw unexpectedMethodCallException();
    }

    @Override
    public void authenticateAsAdmin() {
        throw unexpectedMethodCallException();
    }

    @Override
    public void doAsSystem(@NonNull Runnable runnable) {
        throw unexpectedMethodCallException();
    }

    @Override
    public <V> V callAsSystem(Callable<V> callable) throws Exception {
        throw unexpectedMethodCallException();
    }

    @Override
    public void doAsAdmin(@NonNull Runnable runnable) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void nullifyContext() {
        throw unexpectedMethodCallException();
    }

    @Override
    public SaltedPassword generateSaltedPassword(String rawPassword) {
        throw unexpectedMethodCallException();
    }

    @Override
    public SaltedPassword generateSaltedPassword(String rawPassword, @Nullable String salt) {
        throw unexpectedMethodCallException();
    }

    @Override
    public String getDefaultSalt() {
        throw unexpectedMethodCallException();
    }

    @Override
    public void interceptLoginFailure(@NonNull String userName, long accessTime) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void interceptLoginSuccess(@NonNull String userName) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void ensureUserIsNotDisabled(@NonNull String userName) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void ensureUserIsNotLocked(@NonNull String userName) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void ensureLoginShouldNotBeDelayed(@NonNull String userName) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isUserLocked(String userName) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isUserDisabled(String userName) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void changePassword(String userName, String oldPassword, String newPassword1, String newPassword2) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void changePasswordWithoutValidation(UserInfo user, String newPassword) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void expireUserCredentials(String userName) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void unexpirePassword(String userName) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void expireCredentialsForAllUsers() {
        throw unexpectedMethodCallException();
    }

    @Override
    public void unexpirePasswordForAllUsers() {
        throw unexpectedMethodCallException();
    }

    @Override
    public Set<PasswordExpiryUser> getUsersWhichPasswordIsAboutToExpire() {
        throw unexpectedMethodCallException();
    }

    @Override
    public void markUsersCredentialsExpired(int daysToKeepPassword) {
        throw unexpectedMethodCallException();
    }

    @Override
    public Integer getUserPasswordDaysLeft(String userName) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isUserLockPolicyEnabled() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isPasswordExpirationPolicyEnabled() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isProfileUpdatable(UserInfo userInfo) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean getAndResetForceEnabledBasicAuth() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isUpdatableProfile() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isTransientUser() {
        throw unexpectedMethodCallException();
    }

    @Override
    public String getAuthenticatedRealm() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isAnonAccessEnabled() {
        throw unexpectedMethodCallException();
    }

    @Override
    public @NonNull String currentUsername() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isAdmin() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isAuthenticatedAsSystem() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean hasMcScope() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isAnonymous() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean requireProfileUnlock(@Nullable String overrideRealm, @Nullable String authProviderName) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean requireProfilePassword() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean requireProfilePassword(UserInfo userInfo) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isAuthenticated() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isDisableInternalPassword() {
        throw unexpectedMethodCallException();
    }

    @Override
    public String currentUserEncryptedPassword() {
        throw unexpectedMethodCallException();
    }

    @Override
    public String currentUserEncryptedPasswordOrApiKey() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isApiKeyAuthentication() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isProjectAdmin(@Nullable String projectKey) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isResourceManager() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isProjectAdmin(RepoPath path) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean canDoToRepoProjectResource(RepoActionType repoAction, String repoKey, RepoDetailsType repoType) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean canXrayIndexProjectRepoResource(String repoKey) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isAnyProjectAdmin() {
        throw unexpectedMethodCallException();
    }

    @Override
    public void assertValidAuthInCaseMfaEnabled() {
        throw unexpectedMethodCallException();
    }

    @Override
    public void assertUserAllowedChangePassword(String userName) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void assertUserAllowedToLoginWithMfaEnabled(String userName, boolean uiRequest) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void exportTo(ExportSettings settings) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void importFrom(ImportSettings settings) {
        throw unexpectedMethodCallException();
    }

    private RuntimeException unexpectedMethodCallException() {
        return new UnsupportedOperationException(
                "This is a dummy implementation, none of its methods should be called!");
    }
}
