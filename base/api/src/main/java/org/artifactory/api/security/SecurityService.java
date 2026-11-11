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

package org.artifactory.api.security;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.ImportableExportable;
import org.artifactory.api.repo.RepoActionType;
import org.artifactory.async.Async;
import org.artifactory.descriptor.security.PasswordExpirationPolicy;
import org.artifactory.descriptor.security.UserLockPolicy;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.common.Lock;
import org.artifactory.security.SaltedPassword;
import org.artifactory.security.SecurityInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.util.SerializablePair;

import java.io.IOException;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * User: freds Date: Aug 13, 2008 Time: 5:17:47 PM
 */
public interface SecurityService extends ImportableExportable {

    String FILE_NAME = "security.xml";

    String DEFAULT_ADMIN_USER = "admin";

    String DEFAULT_ADMIN_PASSWORD = "password";

    String USER_SYSTEM = "_system_";

    String USER_XRAY = "xray";

    SecurityInfo getSecurityData();

    SecurityInfo getSecurityData(boolean decryptUserInfoProperties);

    @Lock
    void importSecurityData(String securityXml);

    @Lock
    void importSecurityData(String securityXml, boolean override);

    @Lock
    void importSecurityData(SecurityInfo descriptor);

    void decryptAllUserProps();

    /**
     * @see org.artifactory.itest.ldap.LdapConnectionTesterTest
     */
    BasicStatusHolder testLdapConnection(LdapSetting ldapSetting, String username, String password);

    /**
     * @return True if password encryption is enabled (supported or required).
     */
    public boolean isPasswordEncryptionEnabled();

    /**
     * @return True if the password matches to the password of the currently logged-in user.
     */
    public boolean userPasswordMatches(String passwordToCheck);

    /**
     * Generates a password recovery key for the specified user and send it by mail
     *
     * @param username User to rest his password
     * @param clientIp The IP of the client that sent the request
     */
    @Async(transactional = true)
    void generatePasswordResetKey(String username, String clientIp) throws IOException;

    /**
     * Returns a pair object with the given users password reset key info. If user doesn't exist, a
     * UsernameNotFoundException will be thrown. When the user is not associated with a key, return a null object. In a
     * case where the key is invalid (has less than 3 parts), an IllegalArgumentException is thrown
     *
     * @param username User to retrieve password reset info about
     * @return Pair<Date, String> - Pair containing key generation time and client ip (respectively)
     */
    SerializablePair<Date, String> getPasswordResetKeyInfo(String username);

    /**
     * Returns the given user's last login information
     *
     * @param username Logged in user's name
     * @return Pair<String, Long> - Containing the client IP and last logged in time millis
     */
    SerializablePair<String, Long> getUserLastLoginInfo(String username);

    /**
     * Return true if there was a prior login to artifactory
     */
    boolean isHasPriorLogin();

    /**
     * Updates the user last login information
     *
     * @param username        Logged in user's name
     * @param loginTimeMillis The time of login in millis
     * @param clientIp        The client IP address
     */
    @Async(transactional = true)
    void updateUserLastLogin(String username, long loginTimeMillis, String clientIp);

    /**
     * Updates the user last access information
     *
     * @param username         Name of user that performed an action
     * @param clientIp         The IP of the client that has accessed
     * @param accessTimeMillis The time of access
     */
    void updateUserLastAccess(String username, String clientIp, long accessTimeMillis);

    /**
     * Indicates if Artifactory is configured as proxied by Apache
     *
     * @return True if is proxied. False if not
     */
    boolean isHttpSsoProxied();

    /**
     * Returns the HTTP SSO remote user request variable
     *
     * @return Remote user request variable
     */
    String getHttpSsoRemoteUserRequestVariable();

    /**
     * Indicates if artifactory shouldn't automatically create a user object in the DB for an SSO authenticated user
     *
     * @return True if user should be created in memory. False if user should be created in the DB
     */
    boolean isNoHttpSsoAutoUserCreation();

    void addListener(SecurityListener listener);

    void removeListener(SecurityListener listener);

    void authenticateAsSystem();

    void authenticateAsAdmin();

    /**
     * Do an action (given as {@link Runnable}) as the system user. This method ensures to authenticate as the system
     * user before the action and restore the original authentication after.
     *
     * @param runnable the action to perform
     */
    void doAsSystem(@Nonnull Runnable runnable);

    /**
     * Do an action (given as {@link Callable}) as the system user. This method ensures to authenticate as the system
     * user before the action and restore the original authentication after.
     *
     * @param callable the action to perform
     */
    <V> V callAsSystem(Callable<V> callable) throws Exception;

    /**
     * Do an action (given as {@link Runnable}) as Admin user. This method ensures to authenticate as Administrator
     * user before the action and restore the original authentication after.
     *
     * @param runnable the action to perform
     */
    void doAsAdmin(@Nonnull Runnable runnable);

    void nullifyContext();

    SaltedPassword generateSaltedPassword(String rawPassword);

    SaltedPassword generateSaltedPassword(String rawPassword, @Nullable String salt);

    String getDefaultSalt();

    /**
     * Triggered when user fails to login and
     * locks it if amount of login failures exceeds
     * {@see LockPolicy#loginAttempts}
     *
     * @param userName   user to intercept
     * @param accessTime session creation time
     */
    void interceptLoginFailure(@Nonnull String userName, long accessTime);

    /**
     * Triggered when user success to login
     *
     * @param userName user to intercept
     */
    void interceptLoginSuccess(@Nonnull String userName);

    /**
     * Throws LockedException if user is disabled
     *
     * @throws org.springframework.security.authentication.DisabledException
     */
    void ensureUserIsNotDisabled(@Nonnull String userName);

    /**
     * Throws LockedException if user is locked
     *
     * @throws org.springframework.security.authentication.LockedException
     */
    void ensureUserIsNotLocked(@Nonnull String userName);

    /**
     * @throws org.artifactory.security.exceptions.LoginDisabledException if user has performed
     *                                                                    incorrect login in the past and now should
     *                                                                    wait before performing another login attempt
     */
    void ensureLoginShouldNotBeDelayed(@Nonnull String userName);

    /**
     * Checks whether given user is locked
     *
     * note: this method using caching in sake
     * of DB load preventing
     *
     * @return boolean
     */
    boolean isUserLocked(String userName);

    /**
     * Checks whether given user is disabled
     *
     * @return boolean
     */
    boolean isUserDisabled(String userName);

    /**
     * Changes user password
     *
     * @param userName     user name
     * @param oldPassword  old password
     * @param newPassword1 new password
     * @param newPassword2 replication of new password
     * @return sucess/failure
     */
    void changePassword(String userName, String oldPassword, String newPassword1, String newPassword2);

    /**
     * Changes user password without validating old password
     *
     * @param user        user to change password to
     * @param newPassword new password
     */
    void changePasswordWithoutValidation(UserInfo user, String newPassword);

    /**
     * Makes user password expired
     */
    void expireUserCredentials(String userName);

    /**
     * Makes user password not expired
     */
    void unexpirePassword(String userName);

    /**
     * Makes all users passwords expired
     */
    void expireCredentialsForAllUsers();

    /**
     * Makes all users passwords not expired
     */
    void unexpirePasswordForAllUsers();

    /**
     * Fetches users with password is about to expire
     *
     * @return list of users
     */
    Set<PasswordExpiryUser> getUsersWhichPasswordIsAboutToExpire();

    /**
     * Marks user.credentialsExpired=True where password has expired
     *
     * @param daysToKeepPassword after what period password should be changed
     */
    void markUsersCredentialsExpired(int daysToKeepPassword);

    /**
     * @return number of days left till password will expire
     * or -1 if password already expired or NULL if
     * password expiration feature is disabled
     */
    Integer getUserPasswordDaysLeft(String userName);

    /**
     * @return whether {@link UserLockPolicy} is enabled
     */
    boolean isUserLockPolicyEnabled();

    /**
     * @return whether {@link PasswordExpirationPolicy} is enabled
     */
    boolean isPasswordExpirationPolicyEnabled();

    /**
     * @return True if the user can update his profile
     */
    boolean isProfileUpdatable(UserInfo userInfo);

    /**
     * @return whether basic auth had to be forced enabled due to 0 external auth providers
     */
    boolean getAndResetForceEnabledBasicAuth();

    /**
     * @param forceEnabledBasicAuth whether basic auth had to be forced enabled due to 0 external auth providers
     */
    default void setForceEnabledBasicAuth(boolean forceEnabledBasicAuth) {
        //noop
    }


    /**
     * @return True if the current user can update her profile.
     */
    boolean isUpdatableProfile();

    /**
     * @return True if the current user is transient
     */
    boolean isTransientUser();

    /**
     * @return the authenticated user realm
     */
    String getAuthenticatedRealm();

    /**
     * @return True if anonymous access is allowed.
     */
    boolean isAnonAccessEnabled();

    /**
     * @return The current logged in username. {@link org.artifactory.api.security.SecurityService#USER_SYSTEM} is
     * returned if no login information is found.
     */
    @Nonnull
    String currentUsername();

    /**
     * @return True if the current user is a system administrator.
     */
    boolean isAdmin();

    /**
     * @return True if the current authentication is System's.
     */
    boolean isAuthenticatedAsSystem();

    /**
     * @return True if access token has internal MC scope.
     */
    boolean hasMcScope();

    /**
     * @return True if the current user is anonymous.
     */
    boolean isAnonymous();

    /**
     * @param overrideRealm    user realm, if using access token authentication (UI token)
     * @param authProviderName auth provider name, to match against configured central oauth providers.
     * @return true if should require profile page unlock with password
     */
    boolean requireProfileUnlock(@Nullable String overrideRealm, @Nullable String authProviderName);

    /**
     * @return true if require profile unlock
     */
    boolean requireProfilePassword();

    boolean requireProfilePassword(UserInfo userInfo);

    boolean isAuthenticated();

    boolean isDisableInternalPassword();

    /**
     * @return The current authentication user encrypted password if exists.
     */
    String currentUserEncryptedPassword();

    /**
     * @return The current authentication user encrypted password for internal users or else API key if exists.
     */
    String currentUserEncryptedPasswordOrApiKey();

    boolean isApiKeyAuthentication();

    /**
     * @return True if the current user is the projectKey admin of the given projectKey
     * NOTE that null projectKey is counted as {{@link org.artifactory.projects.ProjectsService#DEFAULT_PROJECT}} and
     * therefore require super admin permissions.
     */
    boolean isProjectAdmin(@Nullable String projectKey);

    boolean isResourceManager();

    /**
     * @return True if the current user is the projectKey admin of the given repository
     */
    boolean isProjectAdmin(RepoPath path);

    /**
     * @return True if the current user can manage the given repository path resource in project scope
     */
    boolean canDoToRepoProjectResource(RepoActionType repoAction, String repoKey, RepoDetailsType repoType);

    /**
     * @return True if the current user can trigger xray indexing on the given repository in project scope,
     * checked via {@link org.jfrog.access.client.project.RoleActions#TRIGGER_SECURITY}
     */
    boolean canXrayIndexProjectRepoResource(String repoKey);

    /**
     * @return True if the current user is a projectKey admin of at least one projectKey
     */
    boolean isAnyProjectAdmin();

    /**
     * Checks if current user has multi factor authentication (MFA) enabled and a valid authentication method
     * (AccessToken only).
     *
     * @throws org.artifactory.exception.http.ForbiddenException if assertion failed.
     */
    void assertValidAuthInCaseMfaEnabled();

    /**
     * Checks if provided in param user has multi factor authentication (MFA) enabled.
     *
     * @throws org.artifactory.exception.http.ForbiddenException if assertion failed.
     */
    void assertUserAllowedChangePassword(String userName);

    /**
     * Checks if user can login with enabled MFA.
     * We will not perform any mfa checks if one of these conditions is true
     * - {@link org.artifactory.common.ConstantValues#loginMfaCheckEnabled} feature flag is disabled
     * - it's a UI request
     * - UI flow will go through Artifactory ('internal' realm is missing),
     * it's a legacy flow that does not support mfa
     * - Access token authentication is used
     *
     * @param userName  user to check
     * @param uiRequest is request coming from UI
     */
    void assertUserAllowedToLoginWithMfaEnabled(String userName, boolean uiRequest);
}
