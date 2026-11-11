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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.EnumUtils;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.model.security.UserProperty;
import org.artifactory.security.ArtifactoryUserStatus;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.SaltedPassword;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserInfo;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.fest.assertions.MapAssert;
import org.jfrog.access.client.auth.UserPrincipal;
import org.jfrog.access.model.Realm;
import org.jfrog.access.model.UserStatus;
import org.jfrog.access.rest.group.GroupResponse;
import org.jfrog.access.rest.user.CustomDatumRequest;
import org.jfrog.access.rest.user.User;
import org.jfrog.access.rest.user.UserRequest;
import org.jfrog.access.rest.user.UserResponse;
import org.jfrog.access.rest.user.UserWithGroups;
import org.jfrog.access.rest.user.UserWithGroupsResponse;
import org.jfrog.access.user.UserMFAStatus;
import org.jfrog.access.user.UsersUtil;
import org.jfrog.security.crypto.CipherAlg;
import org.jfrog.security.crypto.DecodedKeyPair;
import org.jfrog.security.crypto.EncodedKeyPair;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.jfrog.security.crypto.JFrogCryptoHelper;
import org.jfrog.security.crypto.result.DecryptionStatusHolder;
import org.jfrog.security.file.SecurityFolderHelper;
import org.joda.time.format.ISODateTimeFormat;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link UserMapper}.
 *
 * @author Yossi Shaul
 */
@Test
public class UserMapperTest extends ArtifactoryHomeBoundTest {

    @BeforeMethod
    public void setup() {
        homeStub.getArtifactoryKey().delete();
        SecurityFolderHelper.createKeyFile(homeStub.getArtifactoryKey(), CipherAlg.AES128);
    }

    public void fullUserToUserInfo() {
        User u = sampleAccessUser();

        UserInfo i = UserMapper.toArtifactoryUser(u);

        assertEquals(i.getUsername(), u.getUsername());
        assertEquals(i.getEmail(), u.getEmail());
        assertTrue(i.isAccountNonLocked());
        assertFalse(i.isAnonymous());
        assertTrue(i.isAdmin());
        assertTrue(i.isPolicyViewer());
        assertTrue(i.isPolicyManager());
        assertTrue(i.isWatchManager());
        assertTrue(i.isReportsManager());
        assertFalse(i.isGroupAdmin());
        assertFalse(i.isCredentialsExpired());
        assertTrue(i.isEnabled());
        assertNotEquals(i.getStatus(), ArtifactoryUserStatus.LOCKED);
        assertFalse(i.isTransientUser());
        assertFalse(i.isUpdatableProfile());
        assertEquals(i.getGroups(), Sets.newHashSet(InfoFactoryHolder.get().createUserGroup("northmen", "internal"),
                InfoFactoryHolder.get().createUserGroup("kings", "internal")));
        assertEquals(i.getPrivateKey(), u.getCustomData("private_key"));
        assertEquals(i.getPublicKey(), u.getCustomData("public_key"));
        assertThat(i.getUserProperties()).hasSize(5).containsOnly(
                new UserProperty("charismatic", "true"),
                new UserProperty("bold", "true"),
                new UserProperty("height", "210"),
                new UserProperty("blockUiView", "true"),
                new UserProperty("email_verified", "true"));
        assertTrue(i.isDisabledUiAccess());
    }

    public void fullTransientUserToUserInfo() {
        User u = sampleAccessUser().transientUser(true);

        UserInfo i = UserMapper.toArtifactoryUser(u);

        assertEquals(i.getUsername(), u.getUsername());
        assertEquals(i.getEmail(), u.getEmail());
        assertTrue(i.isAccountNonLocked());
        assertFalse(i.isAnonymous());
        assertTrue(i.isAdmin());
        assertTrue(i.isPolicyViewer());
        assertTrue(i.isPolicyManager());
        assertTrue(i.isWatchManager());
        assertTrue(i.isReportsManager());
        assertFalse(i.isGroupAdmin());
        assertFalse(i.isCredentialsExpired());
        assertTrue(i.isEnabled());
        assertNotEquals(i.getStatus(), ArtifactoryUserStatus.LOCKED);
        assertTrue(i.isTransientUser());
        assertFalse(i.isUpdatableProfile());
        assertEquals(i.getGroups(), Sets.newHashSet(InfoFactoryHolder.get().createUserGroup("northmen", "internal"),
                InfoFactoryHolder.get().createUserGroup("kings", "internal")));
        assertEquals(i.getPrivateKey(), u.getCustomData("private_key"));
        assertEquals(i.getPublicKey(), u.getCustomData("public_key"));
        assertThat(i.getUserProperties()).hasSize(5).containsOnly(
                new UserProperty("charismatic", "true"),
                new UserProperty("bold", "true"),
                new UserProperty("height", "210"),
                new UserProperty("blockUiView", "true"),
                new UserProperty("email_verified", "true"));
        assertTrue(i.isDisabledUiAccess());
    }

    public void fullToAccessUserWithArtifactoryEncryptedValues() {
        String encryptedData = ArtifactoryHome.get().getArtifactoryEncryptionWrapper().encryptIfNeeded("data");
        assertNotEquals(encryptedData, "data");

        MutableUserInfo u = sampleUserInfo()
                .addProp(new UserProperty("encrypted", encryptedData))
                .addProp(new UserProperty("decrypted", "newdata"))
                .build();

        EncodedKeyPair keyPair = createKeyPairForUser(u, ArtifactoryHome.get().getArtifactoryEncryptionWrapper());
        DecodedKeyPair decoded =
                keyPair.decode(ArtifactoryHome.get().getArtifactoryEncryptionWrapper(), new DecryptionStatusHolder());
        EncodedKeyPair toValidate = new EncodedKeyPair(decoded, null);

        UserRequest i = UserMapper.toAccessUser(u, true);

        assertEquals(i.getCustomData("encrypted").getValue(), "data");
        assertTrue(i.getCustomData("encrypted").isSensitive());
        assertEquals(i.getCustomData("encrypted_shash").getValue().length(), 6);
        assertFalse(i.getCustomData("encrypted_shash").isSensitive());
        assertEquals(i.getCustomData("decrypted").getValue(), "newdata");
        assertTrue(i.getCustomData("decrypted").isSensitive());
        assertEquals(i.getCustomData("decrypted_shash").getValue().length(), 6);
        assertFalse(i.getCustomData("decrypted_shash").isSensitive());
        assertEquals(i.getCustomData(UsersUtil.BuiltInUserProperty.private_key.name()).getValue(),
                toValidate.getEncodedPrivateKey());
        assertTrue(i.getCustomData(UsersUtil.BuiltInUserProperty.private_key.name()).isSensitive());
        assertEquals(i.getCustomData(UsersUtil.BuiltInUserProperty.public_key.name()).getValue(),
                toValidate.getEncodedPublicKey());
        assertTrue(i.getCustomData(UsersUtil.BuiltInUserProperty.public_key.name()).isSensitive());
    }

    public void fullToAccessUserWithDecryptedPrivatePublicKeys() {
        EncodedKeyPair keyPair = JFrogCryptoHelper.encodeKeyPair(JFrogCryptoHelper.generateKeyPair());
        MutableUserInfo u = sampleUserInfo()
                .privateKey(keyPair.getEncodedPrivateKey())
                .publicKey(keyPair.getEncodedPublicKey())
                .build();

        UserRequest i = UserMapper.toAccessUser(u, true);

        assertEquals(i.getCustomData(UsersUtil.BuiltInUserProperty.private_key.name()).getValue(),
                keyPair.getEncodedPrivateKey());
        assertTrue(i.getCustomData(UsersUtil.BuiltInUserProperty.private_key.name()).isSensitive());
        assertEquals(i.getCustomData(UsersUtil.BuiltInUserProperty.public_key.name()).getValue(),
                keyPair.getEncodedPublicKey());
        assertTrue(i.getCustomData(UsersUtil.BuiltInUserProperty.public_key.name()).isSensitive());
    }

    public void fullToAccessUserWithKeysNewEncryptedValues() {
        MutableUserInfo u = sampleUserInfo().build();

        EncodedKeyPair keyPair = createKeyPairForUser(u, null);
        DecodedKeyPair decoded = keyPair.decode(null, new DecryptionStatusHolder());
        EncodedKeyPair toValidate = new EncodedKeyPair(decoded, null);

        UserRequest i = UserMapper.toAccessUser(u, true);

        assertEquals(i.getCustomData(UsersUtil.BuiltInUserProperty.private_key.name()).getValue(),
                toValidate.getEncodedPrivateKey());
        assertTrue(i.getCustomData(UsersUtil.BuiltInUserProperty.private_key.name()).isSensitive());
        assertEquals(i.getCustomData(UsersUtil.BuiltInUserProperty.public_key.name()).getValue(),
                toValidate.getEncodedPublicKey());
        assertTrue(i.getCustomData(UsersUtil.BuiltInUserProperty.public_key.name()).isSensitive());
    }

    private EncodedKeyPair createKeyPairForUser(MutableUserInfo user, EncryptionWrapper keyWrapper) {
        EncodedKeyPair encodedKeyPair;
        DecodedKeyPair decodedKeyPair = new DecodedKeyPair(JFrogCryptoHelper.generateKeyPair());
        encodedKeyPair = new EncodedKeyPair(decodedKeyPair, keyWrapper);
        user.setPrivateKey(encodedKeyPair.getEncodedPrivateKey());
        user.setPublicKey(encodedKeyPair.getEncodedPublicKey());
        return encodedKeyPair;
    }

    public void fullGroupAdminToUserInfo() {
        UserWithGroups u = sampleAccessGroupAdminUser();
        UserInfo i = UserMapper.toArtifactoryUser(u);
        assertEquals(i.getUsername(), u.getUsername());
        assertEquals(i.getEmail(), u.getEmail());
        assertTrue(i.isAccountNonLocked()); // ?
        assertFalse(i.isAnonymous());
        assertFalse(i.isAdmin());
        assertTrue(i.isGroupAdmin());
        assertFalse(i.isCredentialsExpired());
        assertTrue(i.isEnabled());
        assertNotEquals(i.getStatus(), ArtifactoryUserStatus.LOCKED);
        assertFalse(i.isTransientUser());
        assertFalse(i.isUpdatableProfile());
        assertTrue(i.isDisabledUiAccess());
    }

    public void accessUserToArtifactoryUserWithAllProperties() {
        User u = sampleAccessUser();
        UserInfo userInfo = UserMapper.toArtifactoryUser(u);
        // expect only the 4 custom data properties which aren't mapped to domain fields in UserInfo
        assertThat(userInfo.getUserProperties()).hasSize(5).containsOnly(
                new UserProperty("charismatic", "true"),
                new UserProperty("bold", "true"),
                new UserProperty("height", "210"),
                new UserProperty("blockUiView", "true"),
                new UserProperty("email_verified", "true")
        );
    }

    @Test(dataProvider = "trueFalseDataProvider")
    public void fullUserInfoToAccessUser(boolean xrayManager, boolean xrayReader) {
        UserInfo i = sampleUserInfo(xrayManager, xrayReader).build();

        UserRequest u = UserMapper.toAccessUser(i, true);

        assertEquals(u.getUsername(), i.getUsername());
        assertEquals(u.getPassword(), i.getPassword());
        assertEquals(u.getEmail(), i.getEmail());
        assertTrue(Boolean.parseBoolean(
                u.getCustomData(UsersUtil.BuiltInUserProperty.artifactory_admin.name()).getValue()));
        assertEquals(
                Boolean.parseBoolean(u.getCustomData(UsersUtil.BuiltInUserProperty.policy_viewer.name()).getValue()),
                xrayReader);
        assertEquals(
                Boolean.parseBoolean(u.getCustomData(UsersUtil.BuiltInUserProperty.policy_manager.name()).getValue()),
                xrayManager);
        assertEquals(
                Boolean.parseBoolean(u.getCustomData(UsersUtil.BuiltInUserProperty.watch_manager.name()).getValue()),
                xrayManager);
        assertEquals(
                Boolean.parseBoolean(u.getCustomData(UsersUtil.BuiltInUserProperty.reports_manager.name()).getValue()),
                xrayManager);
        assertFalse(Boolean.parseBoolean(u.getCustomData("updatable_profile").getValue()));
        assertEquals(u.getRealm().getName(), i.getRealm());
        assertEquals(u.getGroups(), Sets.newHashSet("northmen", "kings"));
        assertEquals(u.shouldInvite(), i.shouldInvite());
        assertEquals(u.getSource(), i.getSource());
        assertEquals(u.getCustomData("private_key").getValue(), i.getPrivateKey());
        assertEquals(u.getCustomData("public_key").getValue(), i.getPublicKey());
        assertTrue(u.getCustomData("private_key").isSensitive());
        assertTrue(u.getCustomData("public_key").isSensitive());

        assertEquals(u.getCustomData("charismatic").getValue(), "true");
        assertEquals(u.getCustomData("height").getValue(), "210");
        assertTrue(u.getCustomData("charismatic").isSensitive());
        assertTrue(u.getCustomData("height").isSensitive());
    }

    public void userInfoToAccessUserWithProperties() {
        UserInfo artiUser = sampleUserInfo().build();
        UserRequest accessUser = UserMapper.toAccessUser(artiUser, false);
        assertNotNull(accessUser);
        // make sure custom data include only the Artifactory built in custom properties
        Map<String, CustomDatumRequest> customData = accessUser.getCustomData();
        for (String s : customData.keySet()) {
            assertTrue(EnumUtils.isValidEnum(UsersUtil.BuiltInUserProperty.class, s));
        }
    }

    public void userInfoToAccessUserWithNullProperties() {
        UserInfo artiUser = sampleUserInfo().build();
        UserRequest accessUser = UserMapper.toAccessUser(artiUser, true);
        assertNotNull(accessUser);
        assertThat(toMapOfStrings(accessUser.getCustomData())).includes(MapAssert.entry("charismatic", "true"),
                MapAssert.entry("charismatic", "true"), MapAssert.entry("height", "210"));
    }

    private Map<String, String> toMapOfStrings(Map<String, CustomDatumRequest> customData) {
        HashMap<String, String> map = new HashMap<>(); // Bad java!
        customData.forEach((key, value) -> map.put(key, value.getValue()));
        return map;
    }

    public void testNotSavingEmptyCustomProperties() {
        UserInfo artiUser = sampleUserInfo().addProp(new UserProperty("something", "")).build();
        UserRequest accessUser = UserMapper.toAccessUser(artiUser, true);
        assertNotNull(accessUser);
        Map<String, String> customData = toMapOfStrings(accessUser.getCustomData());
        assertThat(customData).includes(MapAssert.entry("charismatic", "true"),
                MapAssert.entry("charismatic", "true"), MapAssert.entry("height", "210"));
        assertThat(customData.get("something")).isNull();
        assertThat(customData.get("bintray_auth")).isNull();
    }

    public void testDeleteExistPropertiesOnUpdate() {
        MutableUserInfo artiUser = sampleUserInfo().addProp(new UserProperty("something", ""))
                .build();

        UserRequest accessUser = UserMapper.toUpdateUserRequest(artiUser, true);
        assertNotNull(accessUser);
        Map<String, CustomDatumRequest> customData = accessUser.getCustomData();
        assertThat(customData.get("something").getValue()).isNull();
    }

    @Test(dataProvider = "statusDataProvider")
    public void testToArtifactoryUser(UserResponse user, ArtifactoryUserStatus status, boolean isLocked) {
        MutableUserInfo artifactoryUser = UserMapper.toArtifactoryUser(user);
        assertEquals(artifactoryUser.getStatus(), status);
        if (isLocked) {
            assertTrue(artifactoryUser.isLocked());
        } else {
            assertFalse(artifactoryUser.isLocked());
        }
    }

    private UserResponse sampleAccessUser() {
        return new UserResponse()
                .username("bethod")
                .firstName("Bethod")
                .lastName("The King")
                .email("bethod@northmen.com")
                .status(UserStatus.ENABLED)
                .allowedIp("*")
                .created(fromIsoDateString("1978-05-15T09:15:56.003Z"))
                .modified(fromIsoDateString("1978-05-15T09:15:56.003Z"))
                .lastLoginTime(fromIsoDateString("1980-05-15T09:15:56.003Z"))
                .lastLoginIp("10.0.0.2")
                .groups(Sets.newHashSet("northmen", "kings"))
                .realm(Realm.INTERNAL)
                .mfaStatus(UserMFAStatus.NONE)
                .addCustomData(UsersUtil.BuiltInUserProperty.gen_password_key.name(), "ZW4gTmluZWZpbmdlcnMTG9n")
                .addCustomData(UsersUtil.BuiltInUserProperty.updatable_profile.name(), "false")
                .addCustomData(UsersUtil.BuiltInUserProperty.private_key.name(), "TG9nZW4gTmluZWZpbmdlcnM=")
                .addCustomData(UsersUtil.BuiltInUserProperty.public_key.name(), "VGhlIEJsb29keS1OaW5l")
                .addCustomData(UsersUtil.BuiltInUserProperty.artifactory_admin.name(), "true")
                .addCustomData(UsersUtil.BuiltInUserProperty.policy_viewer.name(), "true")
                .addCustomData(UsersUtil.BuiltInUserProperty.policy_manager.name(), "true")
                .addCustomData(UsersUtil.BuiltInUserProperty.watch_manager.name(), "true")
                .addCustomData(UsersUtil.BuiltInUserProperty.reports_manager.name(), "true")
                .addCustomData("charismatic", "true")
                .addCustomData("bold", "true")
                .addCustomData("height", "210")
                .addCustomData("blockUiView", "true")
                .addCustomData("email_verified", "true");
    }

    private UserWithGroupsResponse sampleAccessGroupAdminUser() {
        return new UserWithGroupsResponse()
                .username("bethod")
                .firstName("Bethod")
                .lastName("The King")
                .email("bethod@northmen.com")
                .status(UserStatus.ENABLED)
                .allowedIp("*")
                .created(fromIsoDateString("1978-05-15T09:15:56.003Z"))
                .modified(fromIsoDateString("1978-05-15T09:15:56.003Z"))
                .lastLoginTime(fromIsoDateString("1980-05-15T09:15:56.003Z"))
                .lastLoginIp("10.0.0.2")
                .groups(getAdminUserGroups())
                .realm(Realm.INTERNAL)
                .mfaStatus(UserMFAStatus.NONE)
                .addCustomData(UsersUtil.BuiltInUserProperty.gen_password_key.name(), "ZW4gTmluZWZpbmdlcnMTG9n")
                .addCustomData(UsersUtil.BuiltInUserProperty.updatable_profile.name(), "false")
                .addCustomData(UsersUtil.BuiltInUserProperty.private_key.name(), "TG9nZW4gTmluZWZpbmdlcnM=")
                .addCustomData(UsersUtil.BuiltInUserProperty.public_key.name(), "VGhlIEJsb29keS1OaW5l")
                .addCustomData("charismatic", "true")
                .addCustomData("bold", "true")
                .addCustomData("height", "210")
                .addCustomData("blockUiView", "true");
    }

    private ArrayList<GroupResponse> getAdminUserGroups() {
        Map<String, String> customData = Maps.newHashMap();
        customData.put(UsersUtil.BuiltInUserProperty.artifactory_admin.name(), "true");
        GroupResponse group1 = new GroupResponse().name("group1").realm(Realm.INTERNAL).customData(customData);
        return Lists.newArrayList(group1);
    }

    private static long fromIsoDateString(String dateTime) {
        return ISODateTimeFormat.dateTime().parseMillis(dateTime);
    }

    private UserInfoBuilder sampleUserInfo() {
        return sampleUserInfo(false, false);
    }

    private UserInfoBuilder sampleUserInfo(boolean xrayManager, boolean xrayReader) {
        return new UserInfoBuilder("bethod")
                .password(new SaltedPassword("calder", "scale"))
                .policyViewer(xrayReader)
                .watchManager(xrayManager)
                .policyManager(xrayManager)
                .reportsManager(xrayManager)
                .email("bethod@northmen.com")
                .enabled(true)
                //.created("1978-05-15T09:15:56.003Z")
                //.lastModified("1978-05-15T09:15:56.003Z")
                .groups(Sets.newHashSet(InfoFactoryHolder.get().createUserGroup("northmen", "internal"),
                        InfoFactoryHolder.get().createUserGroup("kings", "internal")))
                .credentialsExpired(false)
                .updatableProfile(false)
                .privateKey("TG9nZW4gTmluZWZpbmdlcnM=")
                .publicKey("VGhlIEJsb29keS1OaW5l")
                .admin(true)
                .realm("internal")
                .shouldInvite(true)
                .lastLogin(fromIsoDateString("1980-05-15T09:15:56.003Z"), "10.0.0.2")
                .addProp(new UserProperty("charismatic", "true"))
                .addProp(new UserProperty("bold", "true"))
                .addProp(new UserProperty("height", "210"))
                .addProp(new UserProperty("blockUiView", "true"));
    }

    @Test(dataProvider = "groupPropertiesProvider")
    public void testUserGroupProperties(Set<UsersUtil.BuiltInUserProperty> groupProperties) {
        UserWithGroupsResponse user = new UserWithGroupsResponse("test-user")
                .status(UserStatus.ENABLED)
                .customData(Map.of(GroupMapper.ArtifactoryBuiltInGroupProperty.artifactory_admin.name(), "false"))
                .mfaStatus(UserMFAStatus.NONE);
        SimpleUser artifactoryUser = UserMapper.toArtifactoryUser(
                new UserPrincipal(user, Set.of(), Set.of(), groupProperties), "pass");
        assertEquals(artifactoryUser.getDescriptor().isGroupAdmin(),
                groupProperties.contains(UsersUtil.BuiltInUserProperty.artifactory_admin));
    }

    @DataProvider
    public static Object[][] trueFalseDataProvider() {
        return new Object[][] {
                {true, true},
                {true, false},
                {false, false},
                {false, true}
        };
    }

    @DataProvider
    public static Object[][] groupPropertiesProvider() {
        return new Object[][] {
                {Set.of(UsersUtil.BuiltInUserProperty.artifactory_admin)},
                {Set.of()},
        };
    }

    @DataProvider
    public static Object[][] statusDataProvider() {
        return new Object[][] {
                {new UserResponse()
                        .mfaStatus(UserMFAStatus.NONE)
                        .username("enabled")
                        .status(UserStatus.ENABLED)
                        .addCustomData(UsersUtil.BuiltInUserProperty.gen_password_key.name(),
                        "ZW4gTmluZWZpbmdlcnMTG9n"), ArtifactoryUserStatus.ENABLED, false},
                {new UserResponse()
                        .mfaStatus(UserMFAStatus.NONE)
                        .username("disabled")
                        .addCustomData(UsersUtil.BuiltInUserProperty.gen_password_key.name(), "ZW4gTmluZWZpbmdlcnMTG9n")
                        .status(UserStatus.DISABLED), ArtifactoryUserStatus.DISABLED, false},
                {new UserResponse()
                        .mfaStatus(UserMFAStatus.NONE)
                        .username("invited")
                        .addCustomData(UsersUtil.BuiltInUserProperty.gen_password_key.name(), "ZW4gTmluZWZpbmdlcnMTG9n")
                        .status(UserStatus.INVITED), ArtifactoryUserStatus.INVITED, false},
                {new UserResponse()
                        .mfaStatus(UserMFAStatus.NONE)
                        .username("locked")
                        .addCustomData(UsersUtil.BuiltInUserProperty.gen_password_key.name(), "ZW4gTmluZWZpbmdlcnMTG9n")
                        .status(UserStatus.LOCKED), ArtifactoryUserStatus.LOCKED, true}

        };
    }
}