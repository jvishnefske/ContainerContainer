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

import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.model.security.UserProperty;
import org.artifactory.security.ArtifactoryUserStatus;
import org.artifactory.security.GroupInfo;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.SaltedPassword;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.UserMFAStatus;
import org.jfrog.access.client.auth.UserPrincipal;
import org.jfrog.access.model.Realm;
import org.jfrog.access.model.UserStatus;
import org.jfrog.access.rest.imports.ImportUserRequest;
import org.jfrog.access.rest.user.CustomDataBuilder;
import org.jfrog.access.rest.user.UpdateUserRequest;
import org.jfrog.access.rest.user.User;
import org.jfrog.access.rest.user.UserBase;
import org.jfrog.access.rest.user.UserRequest;
import org.jfrog.access.rest.user.UserWithGroups;
import org.jfrog.access.user.UsersUtil;
import org.jfrog.common.ClockUtils;
import org.jfrog.security.crypto.DecodedKeyPair;
import org.jfrog.security.crypto.EncodedKeyPair;
import org.jfrog.security.crypto.result.DecryptionStatusHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.storage.db.security.service.access.GroupMapper.fromAccessRealm;
import static org.artifactory.storage.db.security.service.access.GroupMapper.toAccessRealm;
import static org.artifactory.storage.db.security.service.access.UserPropertiesSearchHelper.getSearchableProp;
import static org.artifactory.storage.db.security.service.access.UserPropertiesSearchHelper.isSearchableHelperProperty;

/**
 * Helper class to map Artifactory user to Access user details and vice versa.
 *
 * @author Yossi Shaul
 */
public class UserMapper {

    private static final Logger log = LoggerFactory.getLogger(UserMapper.class);
    public static final int MAX_ALLOWED_USERNAME_LENGTH = 64;

    public static boolean isFieldSensitive(String propKey) {
        return Arrays.stream(UsersUtil.BuiltInUserProperty.values())
                .filter(property -> property.name().equals(propKey))
                .findFirst()
                .map(UsersUtil.BuiltInUserProperty::isSensitive)
                .orElse(true);
    }

    /**
     * Converts an Access user to Artifactory {@link UserInfo}.
     *
     * @param user User model from Access server
     * @return User info built from the Access user
     */
    @Nonnull
    public static MutableUserInfo toArtifactoryUser(@Nonnull UserBase user) {
        //TODO: [by YS] other custom properties (also encrypted)
        UserInfoBuilder builder = new UserInfoBuilder(user.getUsername())
                .email(user.getEmail())
                .admin(user.getBooleanCustomData(UsersUtil.BuiltInUserProperty.artifactory_admin.name()))
                .policyViewer(user.getBooleanCustomData(UsersUtil.BuiltInUserProperty.policy_viewer.name()))
                .policyManager(user.getBooleanCustomData(UsersUtil.BuiltInUserProperty.policy_manager.name()))
                .watchManager(user.getBooleanCustomData(UsersUtil.BuiltInUserProperty.watch_manager.name()))
                .reportsManager(user.getBooleanCustomData(UsersUtil.BuiltInUserProperty.reports_manager.name()))
                .privateKey(user.getCustomData(UsersUtil.BuiltInUserProperty.private_key.name()))
                .publicKey(user.getCustomData(UsersUtil.BuiltInUserProperty.public_key.name()))
                .updatableProfile(user.getBooleanCustomData(UsersUtil.BuiltInUserProperty.updatable_profile.name()))
                .lastLogin(user.getLastLoginTime(), user.getLastLoginIp())
                .credentialsExpired(user.isPasswordExpired())
                .genPasswordKey(user.getCustomData(UsersUtil.BuiltInUserProperty.gen_password_key.name()))
                .groups(groupsFromUser(user))
                .groupAdmin(isGroupAdmin(user))
                .passwordDisabled(user.getBooleanCustomData(UsersUtil.BuiltInUserProperty.disabled_password.name()))
                .password(new SaltedPassword(user.getPasswordHash(), null))
                .status(ArtifactoryUserStatus.getByValue(user.getStatus().getName()))
                .mfaStatus(UserMFAStatus.valueOf(user.getMfaStatus().toString()))
                .realm(fromAccessRealm(user.getRealm()))
                .transientUser(user.isTransientUser())
                .disabledUiAccess(getBooleanCustomData(user, "blockUiView"))
                .emailVerified(getBooleanCustomData(user, "email_verified"));

        user.getCustomData().entrySet().stream()
                .filter(entry -> !UsersUtil.BuiltInUserProperty.contains(entry.getKey()))
                .filter(entry -> !isSearchableHelperProperty(user, entry.getKey()))
                .map(entry -> new UserProperty(entry.getKey(), entry.getValue()))
                .forEach(builder::addProp);

        return builder.build();
    }

    /**
     * Converts an Access user principle to Artifactory {@link SimpleUser}.
     *
     * @param userPrincipal User model from Access server
     * @param password User password
     * @return Simple user built from the Access user
     */
    public static SimpleUser toArtifactoryUser(UserPrincipal userPrincipal, String password) {
        UserWithGroups user = userPrincipal.user();
        Set<UsersUtil.BuiltInUserProperty> userProperties = userPrincipal.userProperties();
        UserInfoBuilder builder = new UserInfoBuilder(user.getUsername())
                .email(StringUtils.isBlank(user.getEmail()) ? null : user.getEmail())
                .admin(userProperties.contains(UsersUtil.BuiltInUserProperty.artifactory_admin))
                .policyViewer(userProperties.contains(UsersUtil.BuiltInUserProperty.policy_viewer))
                .policyManager(userProperties.contains(UsersUtil.BuiltInUserProperty.policy_manager))
                .watchManager(userProperties.contains(UsersUtil.BuiltInUserProperty.watch_manager))
                .reportsManager(userProperties.contains(UsersUtil.BuiltInUserProperty.reports_manager))
                .updatableProfile(userProperties.contains(UsersUtil.BuiltInUserProperty.updatable_profile))
                .passwordDisabled(userProperties.contains(UsersUtil.BuiltInUserProperty.disabled_password))
                .lastLogin(user.getLastLoginTime(), user.getLastLoginIp())
                .credentialsExpired(user.isPasswordExpired())
                .groupAdmin(isGroupAdmin(userPrincipal))
                .groups(UserMapper.groupsFromUser(user))
                .password(new SaltedPassword(password, null))
                .status(ArtifactoryUserStatus.getByValue(user.getStatus().getName()))
                .mfaStatus(UserMFAStatus.valueOf(user.getMfaStatus().toString()))
                .realm(fromAccessRealm(user.getRealm()))
                .transientUser(user.isTransientUser())
                .disabledUiAccess(getBooleanCustomData(user, "blockUiView"))
                .emailVerified(getBooleanCustomData(user, "email_verified"));

        user.getCustomData().forEach((k, v) -> builder.addProp(new UserProperty(k,v)));
        return new SimpleUser(builder.build());
    }

    public static Set<UserGroupInfo> groupsFromUser(UserBase user) {
        return Stream.of(user)
                .flatMap(UserMapper::getUserGroups)
                .map((nameRealm) -> InfoFactoryHolder.get().createUserGroup(nameRealm.getLeft(),
                        fromAccessRealm(nameRealm.getRight())))
                .collect(Collectors.toSet());
    }

    private static Stream<Pair<String, Realm>> getUserGroups(UserBase user) {
        if (user instanceof User) {
            return ((User) user).getGroups().stream()
                    .map((group) -> Pair.of(group, Realm.INTERNAL));
        } else if (user instanceof UserWithGroups) {
            return ((UserWithGroups) user).getGroups().stream()
                    .map((group) -> Pair.of(group.getName(), group.getRealm()));
        }

        throw new IllegalArgumentException(user.getClass().getCanonicalName() +
                " is not a supported child of " + UserBase.class.getCanonicalName());
    }

    private static Boolean isGroupAdmin(UserBase user) {
        if (user instanceof UserWithGroups) {
            return ((UserWithGroups) user).getGroups().stream().map(GroupMapper::toArtifactoryGroup)
                    .anyMatch(GroupInfo::isAdminPrivileges);
        }
        return null;
    }

    public static Boolean isGroupAdmin(UserPrincipal userPrincipal) {
        var groupProperties = userPrincipal.groupProperties();
        if (groupProperties != null && groupProperties.contains(UsersUtil.BuiltInUserProperty.artifactory_admin)) {
            log.debug("User {} is a group admin", userPrincipal.getName());
            return true;
        }
        log.debug("User {} isn't a group admin", userPrincipal.getName());
        return false;
    }

    static UpdateUserRequest toUpdateUserRequest(MutableUserInfo user, boolean includeCustomProperties) {
        return toAccessUser(UpdateUserRequest.create(), user, includeCustomProperties)
                .passwordExpired(user.isCredentialsExpired());
    }

    /**
     * Converts an Artifactory user to Access {@link UserRequest} with or without any extra Artifactory user properties.
     *
     * @param user User model from Artifactory
     * @return User request built from the Artifactory user
     */
    @Nonnull
    public static UserRequest toAccessUser(@Nonnull UserInfo user, boolean includeCustomProperties) {
        return toAccessUser(UserRequest.create(), user, includeCustomProperties);
    }

    @Nonnull
    private static <T extends UserRequest> T toAccessUser(T builder, @Nonnull UserInfo user,
            boolean includeCustomProperties) {
        builder.username(user.getUsername())
                .email(user.getEmail())
                .realm(toAccessRealm(user.getRealm()))
                .status(toAccessStatus(user))
                .groups(user.getGroups().stream().map(UserGroupInfo::getGroupName).collect(Collectors.toSet()));

        // 'UpdateUserRequest' doesn't have nor need these fields
        if (!(builder instanceof UpdateUserRequest)) {
            builder.shouldInvite(user.shouldInvite())
                    .source(user.getSource());
        }
        addCustomData(builder, user, includeCustomProperties);

        if (isUpdate(builder)) {
            if (user.getPassword() != null) {
                builder.password(user.getPassword());
            }
        } else {
            builder.password(user.getPassword());
        }
        return builder;
    }

    /**
     * Converts an Artifactory user to full Access user for import purposes (including any extra Artifactory user
     * properties).
     *
     * @param user User model from Artifactory
     * @return Import User request built from the Artifactory user
     */
    @Nonnull
    public static ImportUserRequest toFullAccessUser(@Nonnull UserInfo user) {
        ImportUserRequest.Builder builder = ImportUserRequest.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .email(user.getEmail())
                .realm(toAccessRealm(user.getRealm()))
                .status(toAccessStatus(user))
                .created(ClockUtils.epochMillis())
                .modified(ClockUtils.epochMillis())
                .lastLoginTime(user.getLastLoginTimeMillis())
                .lastLoginIp(user.getLastLoginClientIp())
                .statusLastModified(ClockUtils.epochMillis())
                .passwordLastModified(user.isCredentialsExpired() ? 0 : getPasswordCreated(user))
                .groups(toUserGroups(user.getGroups()));

        addCustomData(builder, user, true);

        return builder.build();
    }

    private static long getPasswordCreated(UserInfo user) {
        return user.getUserProperty("passwordCreated").filter(StringUtils::isNumeric).map(Long::parseLong)
                .orElseGet(ClockUtils::epochMillis);
    }

    private static UserStatus toAccessStatus(@Nonnull UserInfo user) {
        if (user.getStatus() == null || ArtifactoryUserStatus.ENABLED.equals(user.getStatus())) {
            return UserStatus.ENABLED;
        } else if (ArtifactoryUserStatus.LOCKED.equals(user.getStatus())) {
            return UserStatus.LOCKED;
        } else if (ArtifactoryUserStatus.INVITED.equals(user.getStatus())) {
            return UserStatus.INVITED;
        } else {
            return UserStatus.DISABLED;
        }
    }

    private static void addCustomData(CustomDataBuilder builder, UserInfo user, boolean includeCustomProperties) {
        // Might be already decrypted by access/artifactory, even though there are some cases like converters/import/etc
        // that the data is saved in the old way
        UserInfo decryptedUser = decryptUser(user);

        addCustomData(builder, UsersUtil.BuiltInUserProperty.artifactory_admin, decryptedUser.isAdmin());
        addCustomData(builder, UsersUtil.BuiltInUserProperty.policy_viewer, decryptedUser.isPolicyViewer());
        addCustomData(builder, UsersUtil.BuiltInUserProperty.policy_manager, decryptedUser.isPolicyManager());
        addCustomData(builder, UsersUtil.BuiltInUserProperty.watch_manager, decryptedUser.isWatchManager());
        addCustomData(builder, UsersUtil.BuiltInUserProperty.reports_manager, decryptedUser.isReportsManager());
        addCustomData(builder, UsersUtil.BuiltInUserProperty.private_key, decryptedUser.getPrivateKey());
        addCustomData(builder, UsersUtil.BuiltInUserProperty.public_key, decryptedUser.getPublicKey());
        addCustomData(builder, UsersUtil.BuiltInUserProperty.gen_password_key, decryptedUser.getGenPasswordKey());
        addCustomData(builder, UsersUtil.BuiltInUserProperty.updatable_profile, decryptedUser.isUpdatableProfile());
        addCustomData(builder, UsersUtil.BuiltInUserProperty.disabled_password, decryptedUser.isPasswordDisabled());
        addCustomData(builder, UsersUtil.BuiltInUserProperty.policy_viewer, decryptedUser.isPolicyViewer());

        if (includeCustomProperties) {
            decryptedUser.getUserProperties()
                    .forEach(p -> builder.addCustomData(p.getPropKey(),
                            StringUtils.isBlank(p.getPropValue()) ? null : p.getPropValue(), true));
            //TODO [RK] they should have a special prefix so that they could be differentiated when we get user from
            // access to RT
            // add search hints
            decryptedUser.getUserProperties().stream()
                    .map(p -> getSearchableProp(p.getPropKey(), p.getPropValue()))
                    .filter(Objects::nonNull)
                    .forEach(p -> builder.addCustomData(p.getKey(), p.getValue(), false));
        }
    }

    private static UserInfo decryptUser(UserInfo user) {
        return UserPropertiesSearchHelper.decryptUserProperties(decryptKeys(user));
    }

    private static UserInfo decryptKeys(UserInfo user) {
        if (user.getPrivateKey() != null && user.getPublicKey() != null) {
            EncodedKeyPair keyPair = new EncodedKeyPair(user.getPrivateKey(), user.getPublicKey());

            try {
                // All good?
                keyPair.decode(null, new DecryptionStatusHolder());
            } catch (Exception e) {
                if (ArtifactoryHome.get().getArtifactoryEncryptionWrapper() != null) {
                    log.trace("Keypair is not in the expected format. reformatting", e);
                    return firstFallbackOldArtifactoryFormat(user, keyPair);
                }
            }
        }

        return user;
    }

    private static UserInfo firstFallbackOldArtifactoryFormat(UserInfo user, EncodedKeyPair keyPair) {
        try {
            EncodedKeyPair encodedKeyPair = keyPair.toSaveEncodedKeyPair(
                    ArtifactoryHome.get().getArtifactoryEncryptionWrapper());

            if (encodedKeyPair != null) {
                DecodedKeyPair decoded = encodedKeyPair.decode(ArtifactoryHome.get().getArtifactoryEncryptionWrapper(),
                        new DecryptionStatusHolder());
                return reencodeUser(user, decoded);
            }
        } catch (Exception e) {
            log.trace("Artifactory old format converting failed", e);
        }

        return secondFallbackArtifactoryAESFormat(user, keyPair);
    }

    private static UserInfo secondFallbackArtifactoryAESFormat(UserInfo user, EncodedKeyPair keyPair) {
        try {
            DecodedKeyPair decoded = keyPair.decode(ArtifactoryHome.get().getArtifactoryEncryptionWrapper(),
                    new DecryptionStatusHolder());
            return reencodeUser(user, decoded);
        } catch (Exception e) {
            log.trace("Last fallback failed");
            return user;
        }
    }

    private static UserInfo reencodeUser(UserInfo user, DecodedKeyPair decoded) {
        EncodedKeyPair encodedWithoutKey = new EncodedKeyPair(decoded, null);
        MutableUserInfo userInfo = InfoFactoryHolder.get().copyUser(user);
        userInfo.setPrivateKey(encodedWithoutKey.getEncodedPrivateKey());
        userInfo.setPublicKey(encodedWithoutKey.getEncodedPublicKey());
        return userInfo;
    }

    private static void addCustomData(CustomDataBuilder builder, UsersUtil.BuiltInUserProperty property,
            boolean value) {
        if (value || isUpdate(builder)) {
            builder.addCustomData(property.name(), value ? "true" : null, property.sensitive);
        }
    }

    private static void addCustomData(CustomDataBuilder builder, UsersUtil.BuiltInUserProperty property,
            String value) {
        if (!StringUtils.isBlank(value)) {
            builder.addCustomData(property.name(), value, property.sensitive);
        } else if (isUpdate(builder)) {
            builder.addCustomData(property.name(), null, false);
        }
    }

    private static Set<ImportUserRequest.UserGroup> toUserGroups(Set<UserGroupInfo> groups) {
        return groups.stream()
                .map(group -> new ImportUserRequest.UserGroup(group.getGroupName(), toAccessRealm(group.getRealm())))
                .collect(Collectors.toSet());
    }

    private static boolean getBooleanCustomData(@Nonnull UserBase user, String propKey) {
        return Boolean.parseBoolean(user.getCustomData().get(propKey));
    }

    private static boolean isUpdate(Object builder) {
        return (builder instanceof UpdateUserRequest);
    }
}
