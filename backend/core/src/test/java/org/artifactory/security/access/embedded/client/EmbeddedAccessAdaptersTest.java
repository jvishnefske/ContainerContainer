/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import com.fasterxml.jackson.core.type.TypeReference;
import org.artifactory.platform.PlatformGeneralConfig;
import org.artifactory.security.access.embedded.core.EaUser;
import org.artifactory.security.access.embedded.store.EaChange;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.auth.LoginResult;
import org.jfrog.access.client.auth.ServicePrincipal;
import org.jfrog.access.client.auth.UserPrincipal;
import org.jfrog.access.client.auth.exception.PasswordPolicyException;
import org.jfrog.access.client.auth.ldap.AuthSettingsClient;
import org.jfrog.access.client.model.CrowdSetting;
import org.jfrog.access.client.model.LdapSearchPattern;
import org.jfrog.access.client.model.LdapSetting;
import org.jfrog.access.client.permission.GrpcAffectedEntitiesResponse;
import org.jfrog.access.client.token.TokenRequest;
import org.jfrog.access.client.user.FindUsersRequest;
import org.jfrog.access.common.Action;
import org.jfrog.access.common.AffectedEntitiesType;
import org.jfrog.access.common.ResourceType;
import org.jfrog.access.model.MessageModelCode;
import org.jfrog.access.model.Realm;
import org.jfrog.access.model.UserStatus;
import org.jfrog.access.rest.group.Group;
import org.jfrog.access.rest.group.GroupRequest;
import org.jfrog.access.rest.group.ManageGroupMembersRequest;
import org.jfrog.access.rest.group.UpdateGroupRequest;
import org.jfrog.access.rest.permission.Permission;
import org.jfrog.access.rest.permission.PermissionActionsRequest;
import org.jfrog.access.rest.permission.PermissionRequest;
import org.jfrog.access.rest.user.LoginRequest;
import org.jfrog.access.rest.user.UpdateUserRequest;
import org.jfrog.access.rest.user.User;
import org.jfrog.access.rest.user.UserRequest;
import org.jfrog.access.rest.user.UserWithGroups;
import org.jfrog.access.user.UsersUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The user, group, permission, authentication and configuration adapters over a Derby store. */
class EmbeddedAccessAdaptersTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";

    private EaFixture fx;

    @BeforeEach
    void setUp() {
        fx = new EaFixture();
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    private User createAlice() {
        return fx.client.cachedUsers().createUser(UserRequest.create().username("alice").email("alice@example.org")
                .password(PASSWORD).realm(Realm.INTERNAL).status(UserStatus.ENABLED)
                .addCustomData("color", "blue").addCustomData("api_key_shash", "abc", false)
                .addCustomData(UsersUtil.BuiltInUserProperty.updatable_profile.name(), "true", false));
    }

    private static int status(Runnable r) {
        return assertThrows(AccessClientHttpException.class, r::run).getStatusCode();
    }

    private static String code(Runnable r) {
        return assertThrows(AccessClientHttpException.class, r::run).getErrorsModel().getErrors().get(0).getCode();
    }

    // ---- users ----

    @Test
    void userCrudThroughTheAccessModel() {
        User created = createAlice();
        assertEquals("alice", created.getUsername());
        assertNull(created.getPasswordHash(), "hashes are never returned");
        assertEquals("blue", created.getCustomData("color"));
        assertEquals(UserStatus.ENABLED, created.getStatus());
        assertEquals(Realm.INTERNAL, created.getRealm());
        assertEquals(409, status(this::createAlice));

        User found = fx.client.cachedUsers().findUserByUsername("alice", true).orElseThrow();
        assertEquals("alice@example.org", found.getEmail());
        assertTrue(fx.client.cachedUsers().findUserByUsername("nobody").isEmpty());
        assertEquals(1, fx.client.cachedUsers().findUsersByCustomData("color", "blue", true).getUsers().size());
        assertEquals(1, fx.client.cachedUsers().findUsersByCustomData("_shash", false).getUsers().size());
        assertEquals(0, fx.client.cachedUsers().findUsersByCustomData("_shash", true).getUsers().size());
        assertTrue(fx.client.cachedUsers().findUsers().getUsers().stream()
                .anyMatch(u -> u.getUsername().equals("admin")), "the seeded admin is listed");

        fx.client.cachedUsers().replaceUserLastLogin("alice", 1_700_000_000_000L, "10.0.0.1");
        User afterLogin = fx.client.cachedUsers().findUserByUsername("alice").orElseThrow();
        assertEquals(1_700_000_000_000L, afterLogin.getLastLoginTime());
        assertEquals("10.0.0.1", afterLogin.getLastLoginIp());

        fx.client.cachedUsers().deleteUser("alice");
        assertTrue(fx.client.cachedUsers().findUserByUsername("alice").isEmpty());
        assertEquals(404, status(() -> fx.client.cachedUsers().deleteUser("alice")));
    }

    @Test
    void updateIsAMergePatchAndNullCustomDatumDeletes() {
        createAlice();
        fx.client.cachedUsers().updateUser((UpdateUserRequest) UpdateUserRequest.create().username("alice")
                .addCustomData("color", null).addCustomData("size", "xl", true));
        User u = fx.client.cachedUsers().findUserByUsername("alice").orElseThrow();
        assertNull(u.getCustomData("color"));
        assertEquals("xl", u.getCustomData("size"));
        assertEquals("alice@example.org", u.getEmail(), "fields absent from the patch are kept");
        assertEquals(UserStatus.ENABLED, u.getStatus());

        UpdateUserRequest lock = UpdateUserRequest.create();
        lock.username("alice").status(UserStatus.LOCKED);
        fx.client.cachedUsers().updateUser(lock);
        assertEquals(UserStatus.LOCKED, fx.client.cachedUsers().findUserByUsername("alice").orElseThrow()
                .getStatus());

        UpdateUserRequest addProperty = UpdateUserRequest.create();
        addProperty.username("alice").addCustomData("k", "v", true);
        fx.client.cachedUsers().updateUser(addProperty);
        assertEquals(UserStatus.LOCKED, fx.client.cachedUsers().findUserByUsername("alice").orElseThrow()
                .getStatus(), "a custom-data patch does not reset the status");

        UpdateUserRequest expire = UpdateUserRequest.create();
        expire.passwordExpired(true);
        expire.username("alice");
        fx.client.cachedUsers().updateUser(expire);
        assertTrue(fx.client.cachedUsers().findUserByUsername("alice").orElseThrow().isPasswordExpired());
        assertFalse(fx.client.cachedUsers().findUserByUsername("alice").orElseThrow().getCustomData()
                .keySet().stream().anyMatch(k -> k.startsWith(EaModels.HIDDEN_PREFIX)));
        assertEquals(404, status(() -> fx.client.cachedUsers().updateUser(
                (UpdateUserRequest) UpdateUserRequest.create().username("ghost"))));
    }

    @Test
    void mustChangePasswordDatumMapsToTheColumn() {
        createAlice();
        UpdateUserRequest set = UpdateUserRequest.create();
        set.username("alice").addCustomData(EaModels.MUST_CHANGE_PASSWORD_KEY, "true", false);
        fx.client.cachedUsers().updateUser(set);
        EaUser stored = EaFixture.ok(fx.store.getUser("alice")).orElseThrow();
        assertTrue(stored.mustChangePassword());
        assertFalse(stored.customData().containsKey(EaModels.MUST_CHANGE_PASSWORD_KEY));
        assertEquals("true", fx.client.cachedUsers().findUserWithGroupsByUsername("alice", false).orElseThrow()
                .getCustomData(EaModels.MUST_CHANGE_PASSWORD_KEY));
        UserPrincipal principal = (UserPrincipal) fx.client.grpcAuth(null).authenticate("alice", PASSWORD)
                .getPrincipal();
        assertEquals("true", principal.user().getCustomData(EaModels.MUST_CHANGE_PASSWORD_KEY));

        UpdateUserRequest clear = UpdateUserRequest.create();
        clear.username("alice").addCustomData(EaModels.MUST_CHANGE_PASSWORD_KEY, null);
        fx.client.cachedUsers().updateUser(clear);
        assertFalse(EaFixture.ok(fx.store.getUser("alice")).orElseThrow().mustChangePassword());
        assertNull(fx.client.cachedUsers().findUserByUsername("alice").orElseThrow()
                .getCustomData(EaModels.MUST_CHANGE_PASSWORD_KEY));
    }

    @Test
    void adminFlagMapsToTheColumnAndIsFoundByCustomData() {
        assertEquals(1, fx.client.cachedUsers().findUsersByCustomData(EaModels.ADMIN_KEY, "true", true)
                .getUsers().size(), "the seeded admin");
        assertEquals(1, fx.client.cachedUsers().findUsers(new FindUsersRequest().status(UserStatus.ENABLED))
                .getUsers().size());
    }

    // ---- authentication ----

    @Test
    void passwordAuthentication() {
        createAlice();
        UserWithGroups u = fx.client.cachedAuth().authenticate(new LoginRequest().username("alice")
                .password(PASSWORD));
        assertEquals("alice", u.getUsername());
        LoginResult login = fx.client.grpcAuth(null).uiLogin("alice", PASSWORD);
        assertTrue(login.isSuccessful());
        UserPrincipal p = assertInstanceOf(UserPrincipal.class, login.getPrincipal());
        assertTrue(p.userProperties().contains(UsersUtil.BuiltInUserProperty.updatable_profile));
        assertEquals(MessageModelCode.CODE_UNAUTHORIZED, code(() -> fx.client.grpcAuth(null)
                .authenticate("alice", "wrong")));
        assertEquals(401, status(() -> fx.client.cachedAuth().authenticate(new LoginRequest().username("alice")
                .password("wrong"))));
    }

    @Test
    void unknownUsersStillRunTheComparison() {
        int before = fx.hasher.dummyComparisons.get();
        assertEquals(MessageModelCode.CODE_UNAUTHORIZED, code(() -> fx.client.grpcAuth(null)
                .authenticate("nobody", "whatever")));
        assertEquals(before + 1, fx.hasher.dummyComparisons.get());
    }

    @Test
    void nullHashNeverAuthenticates() {
        int before = fx.hasher.dummyComparisons.get();
        assertEquals(MessageModelCode.CODE_UNAUTHORIZED, code(() -> fx.client.grpcAuth(null)
                .authenticate("admin", "")));
        assertEquals(MessageModelCode.CODE_UNAUTHORIZED, code(() -> fx.client.grpcAuth(null)
                .authenticate("admin", "password")));
        assertEquals(before + 2, fx.hasher.dummyComparisons.get(), "the unclaimed admin has no hash");
    }

    @Test
    void disabledAndLockedUsersNeverAuthenticate() {
        createAlice();
        fx.store.setStatus("alice", EaUser.Status.DISABLED, fx.clock.instant());
        assertEquals(MessageModelCode.CODE_DISABLED, code(() -> fx.client.grpcAuth(null)
                .authenticate("alice", PASSWORD)));
        assertEquals(MessageModelCode.CODE_UNAUTHORIZED, code(() -> fx.client.grpcAuth(null)
                .authenticate("alice", "wrong")), "status is not disclosed without the password");
        fx.store.setStatus("alice", EaUser.Status.LOCKED, fx.clock.instant());
        assertEquals(429, status(() -> fx.client.grpcAuth(null).authenticate("alice", PASSWORD)));
    }

    @Test
    void tokenAsPasswordAndAuthHeader() {
        createAlice();
        String token = fx.client.token().create(TokenRequest.builder()
                .subject(EaFixture.USER_SUBJECT_PREFIX + "alice").scopes(List.of("applied-permissions/user"))
                .expiresIn(3600L).build()).getTokenValue();
        UserPrincipal p = assertInstanceOf(UserPrincipal.class,
                fx.client.grpcAuth(null).authenticate("", token).getPrincipal());
        assertEquals("alice", p.user().getUsername());
        assertEquals(Set.of("applied-permissions/user"), p.tokenScopes());
        assertEquals("alice", fx.client.cachedAuth().loginByAuthHeader("Bearer " + token).getPrincipal()
                .getName());

        String service = fx.client.token().create(TokenRequest.builder().subject(EaFixture.RT.getFormattedName())
                .scopes(List.of("applied-permissions/admin")).expiresIn(3600L).build()).getTokenValue();
        assertInstanceOf(ServicePrincipal.class, fx.client.grpcAuth(null).authenticate("", service)
                .getPrincipal());

        String basic = java.util.Base64.getEncoder().encodeToString(("alice:" + PASSWORD).getBytes());
        assertEquals("alice", fx.client.cachedAuth().loginByAuthHeader("Basic " + basic).getPrincipal().getName());
        assertEquals(MessageModelCode.CODE_TOKEN_VERIFICATION_FAILED, code(() -> fx.client.grpcAuth(null)
                .authenticate("", token.substring(0, token.length() - 4) + "AAAA")));
    }

    @Test
    void passwordPolicyIsApplied() {
        assertThrows(PasswordPolicyException.class, () -> fx.client.cachedAuth().verifyPassword("password"));
        fx.client.cachedAuth().verifyPassword(PASSWORD);
        assertTrue(fx.client.config().getPasswordPolicyEnabled().isEnabled());
    }

    // ---- groups ----

    @Test
    void groupCrudAndMembership() {
        createAlice();
        Group g = fx.client.groups().createGroup(GroupRequest.create().name("devs").description("d")
                .autoJoin(true).addCustomData(EaModels.ADMIN_KEY, "true"));
        assertEquals("devs", g.getName());
        assertEquals("true", g.getCustomData(EaModels.ADMIN_KEY));
        assertTrue(EaFixture.ok(fx.store.getGroup("devs")).orElseThrow().adminPrivileges());

        fx.client.groups().manageGroupUsers("devs", new ManageGroupMembersRequest().addUsers("alice"));
        assertEquals(List.of("alice"), fx.client.groups().findGroupUsers("devs").getUsers().stream()
                .map(User::getUsername).toList());
        UserWithGroups withGroups = fx.client.cachedUsers().findUserWithGroupsByUsername("alice", false)
                .orElseThrow();
        assertEquals("devs", withGroups.getGroups().get(0).getName());
        UserPrincipal p = (UserPrincipal) fx.client.grpcAuth(null).authenticate("alice", PASSWORD).getPrincipal();
        assertTrue(p.groupProperties().contains(UsersUtil.BuiltInUserProperty.artifactory_admin));

        UpdateGroupRequest patch = UpdateGroupRequest.create();
        patch.name("devs").addCustomData(EaModels.ADMIN_KEY, null);
        fx.client.groups().updateGroup(patch);
        Group after = fx.client.groups().findGroupByName("devs").orElseThrow();
        assertNull(after.getCustomData(EaModels.ADMIN_KEY));
        assertEquals("d", after.getDescription());
        assertTrue(after.isAutoJoin());

        assertEquals(1, fx.client.groups().findGroupsByNames(List.of("devs", "nope")).getGroups().size());
        fx.client.groups().manageGroupUsers("devs", new ManageGroupMembersRequest().removeUsers("alice"));
        assertTrue(fx.client.groups().findGroupUsers("devs").getUsers().isEmpty());
        fx.client.groups().deleteGroup("devs");
        assertTrue(fx.client.groups().findGroups().getGroups().isEmpty());
        assertEquals(404, status(() -> fx.client.groups().deleteGroup("devs")));
    }

    // ---- permissions ----

    @Test
    void permissionCrudLastUpdatedAndChangeEvents() throws Exception {
        createAlice();
        LinkedBlockingQueue<GrpcAffectedEntitiesResponse> events = new LinkedBlockingQueue<>();
        fx.client.grpcPermissions().onPermissionsAffected(events::add);
        long before = fx.client.permissions().lastUpdated();

        PermissionRequest request = PermissionRequest.create().displayName("libs").resourceType(ResourceType.REPO)
                .customData("{\"repos\":[\"libs-release\"]}")
                .actions(PermissionActionsRequest.create().addUserAction("alice", Action.READ)
                        .addGroupAction("readers", Action.READ));
        Permission created = fx.client.permissions().createPermission(request);
        assertNotNull(created.getId());
        assertEquals(created.getName(), created.getId());
        assertEquals(EaFixture.RT.getFormattedName(), created.getServiceId());
        assertEquals(List.of(Action.READ), created.getActions().getUserActions().get("alice"));
        assertEquals(409, status(() -> fx.client.permissions().createPermission(request)));
        assertTrue(fx.client.permissions().lastUpdated() > before);

        GrpcAffectedEntitiesResponse event = events.poll(5, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals(AffectedEntitiesType.INVALIDATE, event.getType());

        assertEquals(1, fx.client.grpcPermissions().findPermissionsByResourceType(ResourceType.REPO).size());
        assertTrue(fx.client.grpcPermissions().findPermissionsByResourceType(ResourceType.BUILD).isEmpty());
        UserPrincipal alice = (UserPrincipal) fx.client.grpcAuth(null).authenticate("alice", PASSWORD)
                .getPrincipal();
        assertEquals(1, fx.client.permissions().findPrincipalPermissionsByResourceTypeAndAction(alice,
                ResourceType.REPO, Action.READ, true).getPermissions().size());
        assertEquals(0, fx.client.permissions().findPrincipalPermissionsByResourceTypeAndAction(alice,
                ResourceType.REPO, Action.DEPLOY, true).getPermissions().size());

        PermissionRequest replace = PermissionRequest.create().id(created.getId()).displayName("libs")
                .resourceType(ResourceType.REPO).customData("{}")
                .actions(PermissionActionsRequest.create().addUserAction("alice", Action.DEPLOY));
        Permission replaced = fx.client.permissions().replacePermission(replace);
        assertEquals(List.of(Action.DEPLOY), replaced.getActions().getUserActions().get("alice"));
        assertTrue(replaced.getActions().getGroupActions().isEmpty());
        assertEquals(created.getCreated(), replaced.getCreated());
        assertEquals("libs", fx.client.permissions().findByResourceTypeAndDisplayName(ResourceType.REPO, "libs")
                .orElseThrow().getDisplayName());

        fx.client.permissions().deletePermissionById(created.getId());
        assertTrue(fx.client.permissions().findPermissionById(created.getId()).isEmpty());
        assertEquals(404, status(() -> fx.client.permissions().deletePermissionById(created.getId())));
    }

    @Test
    void tokenRevocationReachesPermissionConsumersAsTheUser() throws Exception {
        LinkedBlockingQueue<GrpcAffectedEntitiesResponse> events = new LinkedBlockingQueue<>();
        fx.client.grpcPermissions().onPermissionsAffected(events::add);
        String token = fx.client.token().create(TokenRequest.builder()
                .subject(EaFixture.USER_SUBJECT_PREFIX + "bob").scopes(List.of("applied-permissions/user"))
                .expiresIn(0L).build()).getTokenValue();
        fx.client.token().revoke(token);
        GrpcAffectedEntitiesResponse event = events.poll(5, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals(List.of("bob"), event.getUserNames());
        assertEquals(AffectedEntitiesType.FULL_INVALIDATION,
                EaGrpcPermissionsClient.toEvent(new EaChange.EverythingChanged()).orElseThrow().getType());
    }

    // ---- configuration documents ----

    @Test
    void securityConfigDeepMergesWithDefaults() {
        assertFalse(fx.client.config().getAnonymousAccessEnabled().isEnabled());
        assertTrue(fx.client.config().getBasicAuthenticationEnabled().isEnabled());
        assertEquals(List.of("internal"), fx.client.config().getEnabledRealms().getEnabledRealms());
        assertFalse(fx.client.config().getMTlsConfiguration().isEnabled());

        Map<String, Object> auth = new HashMap<>();
        auth.put("basic-authentication-enabled", false);
        fx.client.config().updateConfigurationModel(Map.of("security",
                Map.of("anonymous-access-enabled", true, "authentication", auth,
                        "user-lock-policy", Map.of("attempts", 7))));
        fx.client.config().updateConfigurationModel(Map.of("security", Map.of("user-lock-policy",
                Map.of("password-expiry-days", 30))));
        assertTrue(fx.client.config().getAnonymousAccessEnabled().isEnabled());
        assertFalse(fx.client.config().getBasicAuthenticationEnabled().isEnabled());
        assertEquals(7, fx.client.config().getUserLockPolicy().getAttempts());
        assertEquals(30, fx.client.config().getUserLockPolicy().getPasswordExpiryDays());

        Map<String, Object> removal = new HashMap<>();
        removal.put("anonymous-access-enabled", null);
        fx.client.config().updateConfigurationModel(Map.of("security", removal));
        assertFalse(fx.client.config().getAnonymousAccessEnabled().isEnabled());
        assertEquals(7, fx.client.config().getUserLockPolicy().getAttempts());
    }

    @Test
    void platformConfigRoundTripsLombokModels() {
        assertTrue(fx.client.cachedGrpcPlatformConfigs().getConfig("general", PlatformGeneralConfig.class)
                .isEmpty());
        PlatformGeneralConfig config = PlatformGeneralConfig.builder().dateFormat("yyyy").logo("l").build();
        fx.client.cachedGrpcPlatformConfigs().updateConfig("general", old -> config, PlatformGeneralConfig.class,
                false);
        PlatformGeneralConfig read = fx.client.cachedGrpcPlatformConfigs()
                .getConfig("general", PlatformGeneralConfig.class).orElseThrow();
        assertEquals(config, read);

        fx.client.cachedGrpcPlatformConfigs().updateConfig("general", old -> {
            old.setLogo("updated");
            return old;
        }, PlatformGeneralConfig.class, false);
        assertEquals("updated", fx.client.cachedGrpcPlatformConfigs()
                .getConfig("general", PlatformGeneralConfig.class).orElseThrow().getLogo());

        fx.client.cachedGrpcPlatformConfigs().setConfig("list", List.of(config), false);
        List<PlatformGeneralConfig> list = fx.client.cachedGrpcPlatformConfigs().getConfig("list",
                new TypeReference<List<PlatformGeneralConfig>>() {
                });
        assertEquals(List.of(config), list);
        assertTrue(fx.client.cachedGrpcPlatformConfigs().getConfig("absent",
                new TypeReference<List<PlatformGeneralConfig>>() {
                }).isEmpty());
        assertTrue(fx.client.cachedGrpcPlatformConfigs().getBooleanFeatureFlag("x", true));
    }

    @Test
    void authSettingsRoundTripAndEnableRealms() {
        AuthSettingsClient settings = fx.client.cachedAuthSettingClient(null);
        int[] ldapChanges = {0};
        settings.subscribeToLdapChanges(() -> ldapChanges[0]++);
        LdapSetting ldap = LdapSetting.builder().key("corp").enabled(true).ldapUrl("ldap://ldap.example.org")
                .search(LdapSearchPattern.builder().searchFilter("uid={0}").managerPassword("secret").build())
                .isView(true).build();
        settings.upsertLdapSetting(ldap);
        assertEquals(1, ldapChanges[0]);
        assertEquals(List.of(ldap), settings.getAllLdapSettings());
        assertEquals(List.of("internal", "ldap"), fx.client.config().getEnabledRealms().getEnabledRealms());
        assertTrue(settings.isAnyExternalProviderEnabled());

        CrowdSetting crowd = CrowdSetting.builder().enable(true).serverUrl("https://crowd.example.org")
                .applicationName("rt").password("p").build();
        settings.upsertCrowdSetting(crowd);
        assertEquals(crowd, settings.getCrowdSetting().orElseThrow());
        assertTrue(fx.client.config().isRealmEnabled(Realm.CROWD));

        settings.deleteLdapSetting("corp");
        assertTrue(settings.getAllLdapSettings().isEmpty());
        assertEquals(2, ldapChanges[0]);
    }

    @Test
    void systemLicenseAndProjectAnswers() throws Exception {
        assertEquals(EmbeddedAccessClient.SERVER_NAME, assertDoesNotThrowName());
        assertEquals("jfac@01testinstance", fx.client.system().getAccessServiceId().getFormattedName());
        fx.client.cachedLicense().add(EmbeddedAccessClient.SERVER_NAME, "");
        assertTrue(fx.client.cachedGrpcProject().getAllProjectKeys().isEmpty());
        assertNull(fx.client.cachedGrpcProject().getProjectForResource("libs", ResourceType.REPO));
        assertFalse(fx.client.cachedGrpcProject().isProjectAdmin("alice", "default", List.of()));
        assertEquals(List.of("DEV", "PROD"), fx.client.cachedGrpcProject().getGlobalEnvironments());
        assertTrue(fx.client.getTenantID().isEmpty());
    }

    private String assertDoesNotThrowName() {
        try {
            return fx.client.system().getAccessServerName();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }
}
