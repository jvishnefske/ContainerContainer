package org.artifactory.storage.db.security.service.access;

import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.user.UsersClient;
import org.jfrog.access.model.UserStatus;
import org.jfrog.access.rest.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the read-only accessors of {@link AccessUserGroupStoreService}.
 * <p>
 * Verifies the RTDEV-90631 fix: only {@code isUserLocked} - the per-request authentication lock-check that was
 * saturating Access - goes through the Access client's per-process user cache (the {@code withCache=true} overload).
 * Every other read-only accessor (and {@code findUser}) stays on the no-cache path to preserve strong consistency.
 *
 * @author Andrey Tashlitsky
 */
@ExtendWith(MockitoExtension.class)
class AccessUserGroupStoreServiceTest {

    private static final String USER = "cool-user";

    @Mock
    private AccessService accessService;
    @Mock
    private AccessClient accessClient;
    @Mock
    private UsersClient usersClient;

    @InjectMocks
    private AccessUserGroupStoreService service;

    @BeforeEach
    void setUp() {
        when(accessService.getAccessClient()).thenReturn(accessClient);
        when(accessClient.cachedUsers()).thenReturn(usersClient);
        // ensureAuth just runs the supplied call — the auth wrapping is not under test here.
        when(accessService.ensureAuth(any())).thenAnswer(inv -> inv.getArgument(0, Callable.class).call());
    }

    private User userWithStatus(UserStatus status) {
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(status);
        return user;
    }

    private void stubUser(boolean withCache, Optional<User> result) {
        when(usersClient.findUserByUsername(USER, withCache)).thenReturn(result);
    }

    // --- isUserLocked: the only cached accessor (withCache=true) ---

    // R-LOG-03: the lock-check must hit the client cache, not the no-cache overload (the bug being fixed).
    @Test
    void isUserLockedUsesClientCache() {
        stubUser(true, Optional.of(userWithStatus(UserStatus.LOCKED)));

        assertTrue(service.isUserLocked(USER));

        verify(usersClient).findUserByUsername(USER, true);
        verify(usersClient, never()).findUserByUsername(USER, false);
    }

    static Stream<UserStatus> nonLockedStatuses() {
        return Stream.of(UserStatus.ENABLED, UserStatus.DISABLED);
    }

    // R-ERR-02: locked status is derived from the queried payload, not assumed; R-IB-1.2: per-status branches.
    @ParameterizedTest
    @MethodSource("nonLockedStatuses")
    void isUserLockedReturnsFalseForNonLockedStatuses(UserStatus status) {
        stubUser(true, Optional.of(userWithStatus(status)));

        assertFalse(service.isUserLocked(USER));
    }

    // R-IB-1.3: absent user (empty Optional) must not be reported as locked.
    @Test
    void isUserLockedReturnsFalseWhenUserAbsent() {
        stubUser(true, Optional.empty());

        assertFalse(service.isUserLocked(USER));
    }

    // --- isUserDisabled / userExists / getUserPasswordCreationTime: no-cache (withCache=false) ---

    static Stream<UserStatus> nonDisabledStatuses() {
        return Stream.of(UserStatus.ENABLED, UserStatus.LOCKED);
    }

    // R-LOG-03: only the lock-check is cached - isUserDisabled must stay on the no-cache path.
    @Test
    void isUserDisabledBypassesClientCache() {
        stubUser(false, Optional.of(userWithStatus(UserStatus.DISABLED)));

        assertTrue(service.isUserDisabled(USER));

        verify(usersClient).findUserByUsername(USER, false);
        verify(usersClient, never()).findUserByUsername(USER, true);
    }

    @ParameterizedTest
    @MethodSource("nonDisabledStatuses")
    void isUserDisabledReturnsFalseForNonDisabledStatuses(UserStatus status) {
        stubUser(false, Optional.of(userWithStatus(status)));

        assertFalse(service.isUserDisabled(USER));
    }

    @Test
    void isUserDisabledReturnsFalseWhenUserAbsent() {
        stubUser(false, Optional.empty());

        assertFalse(service.isUserDisabled(USER));
    }

    @Test
    void userExistsBypassesClientCacheAndReflectsPresence() {
        stubUser(false, Optional.of(mock(User.class)));

        assertTrue(service.userExists(USER));

        verify(usersClient).findUserByUsername(USER, false);
        verify(usersClient, never()).findUserByUsername(USER, true);
    }

    // R-IB-1.3: absent user must resolve to "does not exist".
    @Test
    void userExistsReturnsFalseWhenUserAbsent() {
        stubUser(false, Optional.empty());

        assertFalse(service.userExists(USER));
    }

    @Test
    void getUserPasswordCreationTimeBypassesClientCacheAndReturnsStoredValue() {
        User user = mock(User.class);
        when(user.getPasswordLastModified()).thenReturn(1_700_000_000_000L);
        stubUser(false, Optional.of(user));

        assertEquals(1_700_000_000_000L, service.getUserPasswordCreationTime(USER));

        verify(usersClient).findUserByUsername(USER, false);
        verify(usersClient, never()).findUserByUsername(USER, true);
    }

    // R-IB-1.3: absent user yields the documented 0L default rather than throwing.
    @Test
    void getUserPasswordCreationTimeReturnsZeroWhenUserAbsent() {
        stubUser(false, Optional.empty());

        assertEquals(0L, service.getUserPasswordCreationTime(USER));
    }

    // --- findUser: no-cache, returns the full record including last-login ---

    // R-ERR-02: findUser returns the full record incl. last-login, which is mutated without cache invalidation,
    // so it must bypass the client cache (withCache=false) to always reflect the latest last-login value.
    @Test
    void findUserBypassesClientCache() {
        when(usersClient.findUserWithGroupsByUsername(USER, false)).thenReturn(Optional.empty());

        service.findUser(USER);

        verify(usersClient).findUserWithGroupsByUsername(USER, false);
        verify(usersClient, never()).findUserWithGroupsByUsername(USER, true);
    }
}
