/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded;

import org.artifactory.common.ConstantValues;
import org.artifactory.factory.InfoFactory;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.platform.PlatformConfigServiceImpl;
import org.artifactory.security.AceInfo;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.security.AuthorizationServiceImpl;
import org.artifactory.security.GroupInfo;
import org.artifactory.security.MutableGroupInfo;
import org.artifactory.security.MutableRepoPermissionTarget;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.RepoAcl;
import org.artifactory.security.SaltedPassword;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.embedded.bootstrap.AdminBootstrapService;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.EaPermission;
import org.artifactory.storage.db.security.service.access.AccessUserGroupStoreService;
import org.artifactory.storage.db.security.service.access.AclStoreServiceImpl;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.rest.user.LoginRequest;
import org.jfrog.access.rest.user.UserWithGroups;
import org.jfrog.security.crypto.EncryptionWrapperFactory;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives Artifactory's own Access-backed services, unchanged, against an {@code EmbeddedAccessClient} over a
 * Derby store built by {@link EmbeddedAccessFactory}: {@link AccessUserGroupStoreService} (users, groups,
 * memberships, must-change flag, bulk password expiry), {@link AclStoreServiceImpl} (permission targets and the
 * {@code permissions.last_updated} polling), {@link AuthorizationServiceImpl} (principal permission lookup) and
 * {@link PlatformConfigServiceImpl}. Only {@link AccessService} is a mock, reduced to handing out the embedded
 * client the way {@code AccessServiceImpl} does in embedded mode. Token mint, verify, refresh and revoke are covered
 * by {@code EmbeddedTokenRoundTripTest} and the restart case by {@link EmbeddedAccessFactoryTest}.
 */
class EmbeddedAccessContractTest extends ArtifactoryHomeBoundJUnitTest {

    private static final ServiceId RT = ServiceId.fromFormattedName("jfrt@01contracttest");
    private static final String ALICE_PASSWORD = "Alice-Password-1234";

    @TempDir
    Path security;

    private final InfoFactory factory = InfoFactoryHolder.get();
    private DerbyMemoryDatabase database;
    private EmbeddedAccess access;
    private AccessUserGroupStoreService users;
    private AclStoreServiceImpl acls;
    private AuthorizationServiceImpl authorization;
    private PlatformConfigServiceImpl platform;

    @BeforeEach
    void setUp() throws Exception {
        homeStub.getArtifactoryProperties().setProperty(ConstantValues.permissionsCacheType.getPropertyName(),
                "legacy");
        homeStub.getArtifactoryProperties().setProperty(
                ConstantValues.aclMinimumWaitRefreshDataSeconds.getPropertyName(), "0");
        database = new DerbyMemoryDatabase("ea-contract");
        access = ok(EmbeddedAccessFactory.create(new EmbeddedAccessFactory.Settings(database.dataSource(),
                DbType.DERBY, RT, EncryptionWrapperFactory.aesKeyWrapperFromString("c".repeat(32)),
                Clock.systemUTC(), Duration.ofHours(1),
                port -> AdminBootstrapService.builder(port, security).env(name -> null).audit(c -> { }).build())));

        AccessService accessService = mock(AccessService.class);
        when(accessService.getAccessClient()).thenReturn(access.client());
        when(accessService.getArtifactoryServiceId()).thenReturn(RT);
        when(accessService.getPlatformConfigClient()).thenReturn(access.client().cachedGrpcPlatformConfigs());
        when(accessService.ensureAuth(any())).thenAnswer(inv -> ((Callable<?>) inv.getArgument(0)).call());

        users = new AccessUserGroupStoreService();
        users.setAccessService(accessService);
        authorization = new AuthorizationServiceImpl();
        authorization.setAccessService(accessService);
        acls = new AclStoreServiceImpl();
        acls.setAccessService(accessService);
        acls.setAuthorizationService(authorization);
        acls.init();
        platform = new PlatformConfigServiceImpl(accessService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        access.close();
        database.close();
    }

    private void createAlice() {
        MutableGroupInfo devs = factory.createGroup("devs");
        devs.setRealm("internal");
        devs.setDescription("developers");
        users.createGroup(devs);
        MutableUserInfo alice = factory.createUser("alice");
        alice.setEmail("alice@example.org");
        alice.setPassword(new SaltedPassword(ALICE_PASSWORD, null));
        alice.setRealm("internal");
        assertTrue(users.createUser(alice));
        users.addUsersToGroup("devs", List.of("alice"));
    }

    @Test
    void usersAndGroupsRoundTripThroughArtifactoryServices() {
        createAlice();
        UserInfo alice = users.findUser("alice");
        assertNotNull(alice);
        assertEquals("alice@example.org", alice.getEmail());
        assertTrue(alice.getGroups().stream().anyMatch(g -> g.getGroupName().equals("devs")));
        assertFalse(alice.isAdmin());
        GroupInfo devs = users.findGroup("devs");
        assertEquals("developers", devs.getDescription());
        assertEquals(List.of("alice"), users.findUsersInGroup("devs"));
        assertTrue(users.userExists("anonymous"), "the seeded anonymous user is visible to Artifactory");
        assertTrue(users.adminUserExists());

        // authenticate as Artifactory's user/password provider does
        UserWithGroups loggedIn = access.client().cachedAuth().authenticate(
                new LoginRequest().username("alice").password(ALICE_PASSWORD));
        assertEquals("alice", loggedIn.getUsername());
        assertThrows(AccessClientHttpException.class, () -> access.client().cachedAuth()
                .authenticate(new LoginRequest().username("alice").password("wrong")));

        users.removeUsersFromGroup("devs", List.of("alice"));
        assertTrue(users.findUsersInGroup("devs").isEmpty());
        users.deleteUser("alice");
        assertNull(users.findUser("alice"));
        assertTrue(users.deleteGroup("devs"));
    }

    @Test
    void mustChangeFlagAndBulkPasswordExpiryRoundTrip() {
        createAlice();
        users.setMustChangePassword("alice", true);
        assertTrue(users.findUser("alice").isMustChangePassword());
        users.setMustChangePassword("alice", false);
        assertFalse(users.findUser("alice").isMustChangePassword());

        users.expirePasswordForAllUsers();
        assertTrue(users.findUser("alice").isCredentialsExpired());
        assertFalse(users.findUser("admin").isCredentialsExpired(), "the passwordless admin has nothing to expire");
        users.revalidatePasswordForAllUsers();
        assertFalse(users.findUser("alice").isCredentialsExpired());
    }

    @Test
    void aclsFollowPermissionChangesAndAuthorizationSeesThem() throws InterruptedException {
        createAlice();
        MutableRepoPermissionTarget target = factory.createRepoPermissionTarget("libs-perm", Set.of("libs-local"));
        AceInfo aliceManages = factory.createAce("alice", false,
                ArtifactoryPermission.READ.getMask() | ArtifactoryPermission.MANAGE.getMask());
        RepoAcl acl = factory.createRepoAcl(target, Set.of(aliceManages), "admin", System.currentTimeMillis());
        acls.createAcl(acl);

        RepoAcl stored = acls.getRepoAcl("libs-perm");
        assertNotNull(stored);
        assertEquals(Set.of("libs-local"), stored.getPermissionTarget().getRepoKeys());
        assertTrue(stored.getAces().stream().anyMatch(a -> a.getPrincipal().equals("alice") && a.canManage()));
        assertTrue(acls.getAclCache().getUserResultMap().containsKey("alice"));

        // a principal permission lookup through Access's API, as AuthorizationServiceImpl does it
        loginAs(users.findUser("alice"));
        assertTrue(authorization.canManage(target));
        assertTrue(authorization.hasRepoPermission(ArtifactoryPermission.MANAGE, false));
        MutableUserInfo bob = factory.createUser("bob");
        bob.setEmail("bob@example.org");
        bob.setRealm("internal");
        users.createUser(bob);
        loginAs(users.findUser("bob"));
        assertFalse(authorization.canManage(target));

        // a change made beneath Artifactory (another writer of the store) reaches the ACL cache by polling
        long before = access.client().permissions().lastUpdated();
        List<EaPermission> stamped = ok(access.store().listPermissions());
        ok(access.store().deletePermission(stamped.get(0).name()));
        assertTrue(access.client().permissions().lastUpdated() > before);
        Thread.sleep(5);
        assertFalse(acls.getAclCache().getUserResultMap().containsKey("alice"));
        assertNull(acls.getRepoAcl("libs-perm"));
    }

    @Test
    void platformConfigRoundTrip() {
        platform.updatePlatformGeneralConfig("dd-MM-yy", true, null);
        assertEquals("dd-MM-yy", platform.getPlatformGeneralConfig().getDateFormat());
        platform.updatePlatformGeneralConfig("yyyy-MM-dd", false, null);
        assertEquals("yyyy-MM-dd", platform.getPlatformGeneralConfig().getDateFormat());
    }

    private static void loginAs(UserInfo user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SimpleUser(user), null, List.of(new SimpleGrantedAuthority("USER"))));
    }

    @SuppressWarnings("unchecked")
    private static <T> T ok(EaOutcome<T> outcome) {
        assertInstanceOf(EaOutcome.Ok.class, outcome, () -> "expected Ok but was " + outcome);
        return ((EaOutcome.Ok<T>) outcome).value();
    }
}
