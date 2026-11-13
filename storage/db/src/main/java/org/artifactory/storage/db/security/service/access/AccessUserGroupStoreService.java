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

package org.artifactory.storage.db.security.service.access;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSetMultimap;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.artifactory.api.security.PasswordExpiryUser;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.md.Properties;
import org.artifactory.model.security.UserImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.sapi.security.SecurityConstants;
import org.artifactory.security.GroupInfo;
import org.artifactory.security.MutableGroupInfo;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.SaltedPassword;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.UserPropertyInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.user.FindUsersRequest;
import org.jfrog.access.client.user.UsersClient;
import org.jfrog.access.model.UserStatus;
import org.jfrog.access.rest.group.Group;
import org.jfrog.access.rest.group.Groups;
import org.jfrog.access.rest.group.ManageGroupMembersRequest;
import org.jfrog.access.rest.user.UpdateUserRequest;
import org.jfrog.access.rest.user.User;
import org.jfrog.access.rest.user.UserBase;
import org.jfrog.access.rest.user.UserRequest;
import org.jfrog.access.user.UsersUtil;
import org.jfrog.client.http.RestRequest;
import org.jfrog.client.http.RestResponse;
import org.jfrog.common.ThrowingFunction;
import org.jfrog.common.config.diff.DataDiff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.artifactory.storage.db.security.service.access.UserPropertiesSearchHelper.addSearchablePropIfNeeded;
import static org.artifactory.storage.db.security.service.access.UserPropertiesSearchHelper.deleteSearchablePropIfNeeded;

/**
 * Implementation of {@link UserGroupStoreService} that user Access as the storage engine.
 *
 * @author Yossi Shaul
 */
@Slf4j
@Service
@Reloadable(beanClass = UserGroupStoreService.class,
        initAfter = AccessService.class,
        listenOn = CentralConfigKey.none)
public class AccessUserGroupStoreService implements UserGroupStoreService {
    private static final String EXPIRE_PASSWORDS_API = "/api/v2/users/expire_password_for_all_users";
    private static final String UNEXPIRE_PASSWORDS_API = "/api/v2/users/unexpire_password_for_all_users";

    @Autowired
    private AccessService accessService;

    public static final String REMEMBER_ME_SCOPE = "authentication:remember-me";

    public enum GroupFilter {
        ALL(in -> true),
        ADMIN(GroupInfo::isAdminPrivileges),
        DEFAULTS(GroupInfo::isNewUserDefault),
        EXTERNAL(gi -> !SecurityConstants.DEFAULT_REALM.equals(gi.getRealm())),
        INTERNAL(gi -> gi.getRealm() == null || gi.getRealm().equals(SecurityConstants.DEFAULT_REALM));

        public final Predicate<GroupInfo> filterFunction;

        GroupFilter(Predicate<GroupInfo> filterFunction) {
            this.filterFunction = filterFunction;
        }
    }

    @Override
    public boolean createUser(UserInfo user) {
        return createUserWithProperties(user, false);
    }

    @Override
    public boolean createUserWithProperties(UserInfo user, boolean addUserProperties) {
        if (userExists(user.getUsername())) {
            return false;
        }
        // create a copy of the user
        user = copyUser(user);
        UserRequest accessUser = UserMapper.toAccessUser(user, addUserProperties);
        getClient().cachedUsers().createUser(accessUser);
        return true;
    }

    @Override
    public void updateUser(MutableUserInfo userInfo) {
        if (!Strings.isNullOrEmpty(userInfo.getPassword())) {
            getClient().token().revokeAllForUserAndScope(userInfo.getUsername(), REMEMBER_ME_SCOPE);
        }
        getClient().cachedUsers().updateUser(UserMapper.toUpdateUserRequest(copyUser(userInfo), true));
    }

    private UserImpl copyUser(UserInfo user) {
        return new UserImpl(user);
    }

    @Override
    public UserInfo findUser(String username) {
        // No cache: returns last-login, which is updated without a SecurityChanges(users) invalidation,
        // so a cached value would be stale (see getUserLastLoginInfo).
        return findUserInternal(username, true, false)
                .map(UserMapper::toArtifactoryUser)
                .map(UserPropertiesSearchHelper::decryptUserProperties)
                .orElse(null);
    }

    private Optional<? extends UserBase> findUserInternal(String username, boolean withGroups, boolean withCache) {
        // Only isUserLocked passes withCache=true: it runs on the per-request authentication lock-check and was
        // saturating Access with one GET /users/{username} per request (RTDEV-90631). The Access client's
        // per-process user cache is kept coherent by gRPC PermissionsAffected invalidation on lock/unlock, so the
        // cached status can never report a stale "not locked". All other accessors stay on the no-cache path.
        UsersClient usersClient = getClient().cachedUsers();
        return accessService.ensureAuth(
                () -> withGroups ?
                        usersClient.findUserWithGroupsByUsername(username, withCache) :
                        usersClient.findUserByUsername(username, withCache)
        );
    }

    @Override
    public void deleteAllGroupsAndUsers() {
        //TODO: [by YS] add api entry point in Access server unless it's only required for the import
        getAllGroups().forEach(g -> getClient().groups().deleteGroup(g.getGroupName()));
        getAllUsers(true, false, true).forEach(u -> getClient().cachedUsers().deleteUser(u.getUsername()));
    }

    @Override
    public boolean adminUserExists() {
        return isNotEmpty(getClient().cachedUsers()
                .findUsersByCustomData(UsersUtil.BuiltInUserProperty.artifactory_admin.name(), "true", true)
                .getUsers());
    }

    @Override
    public boolean userExists(String username) {
        return findUserInternal(username, false, false).isPresent();
    }

    @Override
    public void deleteUser(String username) {
        try {
            getClient().cachedUsers().deleteUser(username);
        } catch (AccessClientHttpException e) {
            if (e.getStatusCode() != 404) {
                throw e;
            }
        }
    }

    @Nullable
    @Override
    public UserInfo findUserByProperty(String key, String val, boolean exactKeyMatch) {
        return UserPropertiesSearchHelper.findUserByProperty(getClient(), key, val, exactKeyMatch);
    }

    @Nullable
    @Override
    public String findUserProperty(String username, String key) {
        UserInfo user = findUser(username);
        if (user == null) {
            log.debug("The user: '{}' doesn't exist. Cannot find property", username);
            return null;
        }

        return user.getUserProperty(key)
                .filter(StringUtils::isNotBlank)
                .map(v -> CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), v))
                .orElse(null);
    }

    @Override
    public boolean addUserProperty(String username, String key, String val) {
        if (isReservedPropertyKey(key)) {
            log.warn("Refusing to set reserved user property '{}' for user '{}'", key, username);
            return false;
        }
        if (!userExists(username)) {
            log.debug("The user: '{}' doesn't exist. Cannot add property", username);
            return false;
        }
        UpdateUserRequest updateUserRequest = UpdateUserRequest.create();
        updateUserRequest.username(username).addCustomData(key, val, true);
        addSearchablePropIfNeeded(updateUserRequest, key, val);
        getClient().cachedUsers().updateUser(updateUserRequest);
        return true;
    }

    @Override
    public boolean deleteUserProperty(String username, String propKey) {
        if (isReservedPropertyKey(propKey)) {
            log.warn("Refusing to delete reserved user property '{}' for user '{}'", propKey, username);
            return false;
        }
        if (!userExists(username)) {
            log.debug("The user: '{}' doesn't exist. Cannot delete property {}", username, propKey);
            return false;
        }

        UserInfo user = findUser(username);
        if (user == null) {
            log.debug("The user: '{}' doesn't exist. Cannot delete property {}", username, propKey);
            return false;
        }
        return deleteUserProperty(user, propKey);
    }

    private boolean deleteUserProperty(UserInfo user, String propKey) {
        boolean propertyExist = user.getUserProperty(propKey).isPresent();
        if (!propertyExist) {
            log.debug("The user: '{}' doesn't have the specified property: {}.", user.getUsername(), propKey);
            return false;
        }

        UpdateUserRequest updateUserRequest = UpdateUserRequest.create();
        updateUserRequest.username(user.getUsername()).addCustomData(propKey, null);
        deleteSearchablePropIfNeeded(updateUserRequest, propKey);
        getClient().cachedUsers().updateUser(updateUserRequest);
        return true;
    }

    @Override
    public void deletePropertyFromAllUsers(String propertyKey) {
        if (isReservedPropertyKey(propertyKey)) {
            log.warn("Refusing to delete reserved user property '{}' from all users", propertyKey);
            return;
        }
        getClient().cachedUsers().findUsersByCustomData(propertyKey, true).getUsers().stream()
                .map(UserMapper::toArtifactoryUser)
                .forEach(u -> deleteUserProperty(u, propertyKey));
    }

    @Override
    public Properties findPropertiesForUser(String username) {
        UserInfo user = findUser(username);
        return findPropertiesForUser(user);
    }

    @Override
    public Properties findPropertiesForUser(UserInfo user) {
        if (user == null) {
            log.debug("Null user cannot be found. Returning empty properties");
            return new PropertiesImpl();
        }

        PropertiesImpl properties = new PropertiesImpl();
        for (UserPropertyInfo userProperty : user.getUserProperties()) {
            properties.put(userProperty.getPropKey(),
                    userProperty.getPropValue());
        }
        return properties;
    }

    @Override
    public List<UserInfo> getAllUsers(boolean includePasswordsAndEncrypted, boolean includePasswords,
            boolean decryptUserInfoProperties) {
        return getAllUsersInternal(includePasswordsAndEncrypted, includePasswords, decryptUserInfoProperties, null);
    }

    private List<UserInfo> getAllUsersInternal(boolean includeAdmins, boolean secured,
            boolean decryptUserInfoProperties, Collection<String> groups) {
        Stream<UserInfo> userInfoStream = getAllUsersInternal(secured).stream()
                .map(UserMapper::toArtifactoryUser)
                .map(user -> decryptUserInfoProperties ? UserPropertiesSearchHelper.decryptUserProperties(user) : user)
                .filter(userInfo -> includeAdmins || !userInfo.isAdmin());

        Collection<String> groupAdmins = groups == null ? getAllAdminGroupsNames() : groups;
        userInfoStream = userInfoStream.map(u -> {
            boolean groupAdmin = u.getGroups().stream()
                    .anyMatch(group -> groupAdmins.contains(group.getGroupName()));
            MutableUserInfo copyUser = InfoFactoryHolder.get().copyUser(u);
            copyUser.setGroupAdmin(groupAdmin);
            return copyUser;
        });
        return userInfoStream.collect(Collectors.toList());
    }

    @Override
    public Map<String, Boolean> getAllUsersAndAdminStatus(boolean justAdmins, Collection<String> groups) {
        List<UserInfo> users = getAllUsersInternal(true, false, true, groups);
        return filterUsersByAdminStatus(justAdmins, users);
    }

    @Override
    public Map<String, Boolean> filterUsersByAdminStatus(boolean justAdmins,
            List<UserInfo> allUsers) {
        return allUsers.stream()
                .filter(u -> !justAdmins || u.isEffectiveAdmin())
                .collect(Collectors.toMap(UserInfo::getUsername, UserInfo::isEffectiveAdmin));
    }

    @Override
    public List<String> getAllAdminGroupsNames() {
        return getGroupsByFilter(GroupFilter.ADMIN).stream().map(GroupInfo::getGroupName).collect(Collectors.toList());
    }

    private List<User> getAllUsersInternal(boolean secured) {
        if (!secured) {
            return getClient().cachedUsers().findUsers().getUsers();
        }

        return getClient().cachedUsers().findUsers(new FindUsersRequest()
                .expand(UserRequest.Expand.passwords)
                .expand(UserRequest.Expand.encryptedData)
        ).getUsers();
    }

    @Override
    public ImmutableSetMultimap<String, String> getAllUsersInGroups() {
        return getAllUsersInGroups(getAllUsers(true, false, true));
    }

    @Override
    public ImmutableSetMultimap<String, String> getAllUsersInGroups(List<UserInfo> allUsers) {
        ImmutableSetMultimap.Builder<String, String> builder = ImmutableSetMultimap.builder();
        allUsers.forEach(userInfo -> builder.putAll(
                userInfo.getUsername(),
                userInfo.getGroups()
                        .stream()
                        .map(UserGroupInfo::getGroupName)
                        .collect(Collectors.toList()))
        );

        return builder.build();
    }

    @Override
    public List<UserInfo> getAllUsers(boolean includeAdmins, boolean includePasswordsAndEncrypted,
            boolean decryptUserInfoProperties, Collection<String> groups) {
        return getAllUsersInternal(includeAdmins, includePasswordsAndEncrypted, decryptUserInfoProperties, groups);
    }

    @Override
    public void updateUserLastLogin(String username, long lastLoginTimeMillis, String lastLoginIp) {
        getClient().cachedUsers().replaceUserLastLogin(username, lastLoginTimeMillis, lastLoginIp);
    }

    @Override
    public void lockUser(@Nonnull String username) {
        handleLockException(ignore ->
                        patchUser(createUpdateUserRequest(username, request -> request.status(UserStatus.LOCKED))),
                username,
                "lock");
    }

    @Override
    public void unlockUser(@Nonnull String username) {
        handleLockException(ignore ->
                        patchUser(createUpdateUserRequest(username, (request) -> request.status(UserStatus.ENABLED)))
                , username,
                "unlock");
    }

    private void handleLockException(ThrowingFunction<Void, User, Exception> execute, String username,
            String operation) {
        try {
            execute.apply(null);
        } catch (AccessClientHttpException e) {
            if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                log.debug("The user: '{}' to {} not found. Ignoring", username, operation);
                return;
            }
            throwStorageException(username, operation, e);
        } catch (Exception e) {
            throwStorageException(username, operation, e);
        }
    }

    private void throwStorageException(String username, String operation, Exception e) {
        log.debug("Could not {} user: '{}', cause: {}", operation, username, e);
        throw new StorageException("Could not lock user: '" + username + "', reason: " + e.getMessage());
    }

    @Override
    public boolean isUserLocked(String userName) {
        // Cached (withCache=true): this is the per-request lock-check that saturated Access (RTDEV-90631).
        // lock/unlock publish a SecurityChanges(users) gRPC event that invalidates the cached status on every node.
        return findUserInternal(userName, false, true)
                .map(UserBase::getStatus)
                .map(UserStatus.LOCKED::equals)
                .orElse(false);
    }

    @Override
    public boolean isUserDisabled(String userName) {
        return findUserInternal(userName, false, false)
                .map(UserBase::getStatus)
                .map(UserStatus.DISABLED::equals)
                .orElse(false);
    }

    private AccessClient getClient() {
        return accessService.getAccessClient();
    }

    @Override
    public void unlockAllUsers() {
        getLockedUsers().forEach(this::unlockUser);
    }

    @Override
    public Set<String> getLockedUsers() {
        return getAllUsersInternal(false)
                .stream()
                .filter(user -> UserStatus.LOCKED.equals(user.getStatus()))
                .map(User::getUsername)
                .collect(Collectors.toSet());
    }

    private User patchUser(UpdateUserRequest userRequest) {
        return getClient().cachedUsers().updateUser(userRequest);
    }

    private UpdateUserRequest createUpdateUserRequest(String username,
            Function<UpdateUserRequest, UserRequest> updates) {
        return (UpdateUserRequest) updates.apply((UpdateUserRequest) UpdateUserRequest.create().username(username));
    }

    @Override
    public void changePassword(UserInfo user, SaltedPassword newSaltedPassword, String rawPassword) {
        patchUser(createUpdateUserRequest(user.getUsername(),
                (request) -> request.password(rawPassword)));
    }

    @Override
    public void setMustChangePassword(@Nonnull String username, boolean mustChangePassword) {
        patchUser(createUpdateUserRequest(username,
                (request) -> request.addCustomData(UserMapper.MUST_CHANGE_PASSWORD_KEY,
                        mustChangePassword ? "true" : null, false)));
    }

    /**
     * The must-change-password flag lives in the same Access custom data as user properties; it is managed only by
     * {@link #setMustChangePassword(String, boolean)} so a property write cannot lift it.
     */
    private static boolean isReservedPropertyKey(String key) {
        return UserMapper.MUST_CHANGE_PASSWORD_KEY.equals(key);
    }

    @Override
    public boolean isUserPasswordExpired(String userName, int expiresIn) {
        return Optional.ofNullable(findUser(userName)).map(UserInfo::isCredentialsExpired).orElse(false);
    }

    @Override
    public void expireUserPassword(String userName) {
        patchUser(createUpdateUserRequest(userName,
                (request) -> request.passwordExpired(true)));
    }

    @Override
    public void changeUserGenPassword(@Nonnull String userName, String genPassword) {
        patchUser(createUpdateUserRequest(userName,
                (request) -> request
                        .addCustomData(UsersUtil.BuiltInUserProperty.gen_password_key.name(), genPassword)));
    }

    @Override
    public void revalidatePassword(String userName) {
        patchUser(createUpdateUserRequest(userName,
                (request) -> request.passwordExpired(false)));
    }

    @Override
    public void expirePasswordForAllUsers() {
        callPasswordExpiryApi(EXPIRE_PASSWORDS_API, "expire");
    }

    @Override
    public void revalidatePasswordForAllUsers() {
        callPasswordExpiryApi(UNEXPIRE_PASSWORDS_API, "unexpire");
    }

    /**
     * Expires or revalidates every password. A client that implements {@link BulkPasswordExpiry} (the embedded
     * Access client, which refuses raw REST calls) does it directly; any other client gets the stock Access REST
     * call.
     */
    private void callPasswordExpiryApi(String apiPath, String action) {
        AccessClient client = getClient();
        if (client instanceof BulkPasswordExpiry bulk) {
            int changed = bulk.setPasswordExpiredForAllUsers(EXPIRE_PASSWORDS_API.equals(apiPath));
            log.info("Password {} applied to {} users", action, changed);
            return;
        }
        RestResponse response = client.restCall(RestRequest.post(apiPath).build());
        if (!response.isSuccessful()) {
            throw new StorageException(String.format(
                    "Failed to %s password for all users via Access API. Status: %d, Response: %s",
                    action, response.getStatusCode(), response.getBodyAsString()));
        }
    }

    @Override
    public List<String> markUsersCredentialsExpired(int daysToKeepPassword) {
        // [NS] Irrelevant, access manages expiry of users
        return Collections.emptyList();
    }

    @Override
    public void expirePasswordForUserIds(Set<Long> userIds) throws SQLException {
        // [NS] Irrelevant, access manages different ids
    }

    @Override
    public Long getUserPasswordCreationTime(String userName) {
        return findUserInternal(userName, false, false).map(UserBase::getPasswordLastModified).orElse(0L);
    }

    @Override
    public Set<PasswordExpiryUser> getUsersWhichPasswordIsAboutToExpire(int daysToNotifyBefore,
            int daysToKeepPassword) {
        return accessService.getAccessClient().cachedUsers()
                .findUsers(new FindUsersRequest().daysToExpire(daysToNotifyBefore))
                .getUsers().stream()
                .map(user -> Pair.of(user, UserMapper.toArtifactoryUser(user)))
                .filter(users -> !users.getRight().isPasswordDisabled())
                .filter(users -> SecurityConstants.DEFAULT_REALM.equals(users.getRight().getRealm()))
                .map(users -> new PasswordExpiryUser(
                        users.getLeft().getUsername(), users.getLeft().getEmail(),
                        users.getLeft().getPasswordLastModified()))
                .collect(Collectors.toSet());
    }

    @Nullable
    @Override
    public GroupInfo findGroup(String groupName) {
        Optional<Group> group = getClient().groups().findGroupByName(groupName);
        return group.map(GroupMapper::toArtifactoryGroup).orElse(null);
    }

    @Override
    public Group createGroup(GroupInfo groupInfo) {
        return getClient().groups().createGroup(GroupMapper.toAccessGroup(groupInfo));
    }

    @Override
    public void addUsersToGroup(String groupName, Collection<String> usernames) {
        accessService.getAccessClient().groups().manageGroupUsers(groupName,
                new ManageGroupMembersRequest().addUsers(usernames.toArray(new String[] {})));
    }

    @Override
    public void removeUsersFromGroup(String groupName, Collection<String> usernames) {
        accessService.getAccessClient().groups().manageGroupUsers(groupName,
                new ManageGroupMembersRequest().removeUsers(usernames.toArray(new String[] {})));

    }

    @Override
    public List<String> findUsersInGroup(String groupName) {
        return accessService.getAccessClient()
                .groups()
                .findGroupUsers(groupName).getUsers()
                .stream()
                .map(User::getUsername)
                .collect(Collectors.toList());
    }

    @Override
    public boolean deleteGroup(String groupName) {
        try {
            getClient().groups().deleteGroup(groupName);
            return true;
        } catch (AccessClientHttpException e) {
            log.debug("Delete group failed: {}", e.getStatusCode(), e);
            if (e.getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<GroupInfo> getAllGroups() {
        Groups groups = getClient().groups().findGroups();
        return groups.getGroups().stream().map(GroupMapper::toArtifactoryGroup).collect(Collectors.toList());
    }

    @Override
    public Map<String, GroupInfo> getAllGroupsByNames(List<String> groupNames) {
        return getClient()
                .groups()
                .findGroupsByNames(groupNames)
                .getGroups()
                .stream()
                .collect(Collectors.toMap(Group::getName, GroupMapper::toArtifactoryGroup));
    }

    private List<GroupInfo> getGroupsByFilter(GroupFilter filter) {
        return getAllGroups().stream().filter(filter.filterFunction).collect(Collectors.toList());
    }

    @Override
    public List<GroupInfo> getNewUserDefaultGroups() {
        return getGroupsByFilter(GroupFilter.DEFAULTS);
    }

    @Override
    public List<GroupInfo> getAllExternalGroups() {
        return getGroupsByFilter(GroupFilter.EXTERNAL);
    }

    @Override
    public List<GroupInfo> getInternalGroups() {
        return getGroupsByFilter(GroupFilter.INTERNAL);
    }

    @Override
    public Set<String> getNewUserDefaultGroupsNames() {
        return getNewUserDefaultGroups().stream().map(GroupInfo::getGroupName).collect(Collectors.toSet());
    }

    @Override
    public Group updateGroup(MutableGroupInfo groupInfo) {
        return getClient().groups().updateGroup(GroupMapper.toUpdateAccessGroup(groupInfo));
    }

    @Override
    public void init() {
        if (accessService.getAccessClient() == null) {
            throw new IllegalStateException("Access client cannot be null at this point");
        }
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        // nop
    }

    @VisibleForTesting
    public void setAccessService(AccessService accessService) {
        this.accessService = accessService;
    }
}
