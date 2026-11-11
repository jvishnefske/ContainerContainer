package org.artifactory.storage.db.security.service;

import org.artifactory.common.ConstantValues;
import org.artifactory.security.access.AccessService;
import org.artifactory.storage.security.service.UserLockInMemoryService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.rest.system.UserLockPolicyConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.artifactory.security.UserInfo.ANONYMOUS;
import static org.fest.assertions.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Nadav Yogev
 * @author Tomer Slutsky
 */
public class UserLockInMemoryServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private static Stream<Arguments> dataProvider() {
        return Stream.of(
                Arguments.of(1, -1, 128, 129, 2),
                Arguments.of(2, 128, 128, 129, 2),
                Arguments.of(3, 129, 128, 129, 2),
                Arguments.of(4, 129, 128, 129, 2),
                Arguments.of(100, 129, 128, 129, 2),
                Arguments.of(2, -1, 128, 512, 3),
                Arguments.of(3, 128, 128, 512, 3),
                Arguments.of(4, 256, 128, 512, 3),
                Arguments.of(5, 512, 128, 512, 3),
                Arguments.of(6, 512, 128, 512, 3),
                Arguments.of(1, 128, 128, 256, 1),
                Arguments.of(2, 256, 128, 256, 1),
                Arguments.of(4, 256, 128, 256, 1),
                Arguments.of(1, -1, 0, 1000, 2),
                Arguments.of(2, -1, 0, 1000, 2),
                Arguments.of(10, -1, 0, 1000, 2),
                Arguments.of(100, -1, 0, 1000, 2),
                Arguments.of(1, -1, 128, 0, 2),
                Arguments.of(2, -1, 128, 0, 2),
                Arguments.of(10, -1, 128, 0, 2),
                Arguments.of(100, -1, 128, 0, 2),
                Arguments.of(1, -1, Integer.parseInt(ConstantValues.loginBlockDelayMillis.getDefValue()),
                        Integer.parseInt(ConstantValues.loginMaxBlockDelayMillis.getDefValue()),
                        Integer.parseInt(ConstantValues.maxIncorrectLoginAttempts.getDefValue()),
                        Arguments.of(2, 1000, Integer.parseInt(ConstantValues.loginBlockDelayMillis.getDefValue()),
                                Integer.parseInt(ConstantValues.loginMaxBlockDelayMillis.getDefValue()),
                                Integer.parseInt(ConstantValues.maxIncorrectLoginAttempts.getDefValue())),
                        Arguments.of(3, 2000, Integer.parseInt(ConstantValues.loginBlockDelayMillis.getDefValue()),
                                Integer.parseInt(ConstantValues.loginMaxBlockDelayMillis.getDefValue()),
                                Integer.parseInt(ConstantValues.maxIncorrectLoginAttempts.getDefValue())),
                        Arguments.of(4, 6000, Integer.parseInt(ConstantValues.loginBlockDelayMillis.getDefValue()),
                                Integer.parseInt(ConstantValues.loginMaxBlockDelayMillis.getDefValue()),
                                Integer.parseInt(ConstantValues.maxIncorrectLoginAttempts.getDefValue())),
                        Arguments.of(6, 33000, Integer.parseInt(ConstantValues.loginBlockDelayMillis.getDefValue()),
                                Integer.parseInt(ConstantValues.loginMaxBlockDelayMillis.getDefValue()),
                                Integer.parseInt(ConstantValues.maxIncorrectLoginAttempts.getDefValue())),
                        Arguments.of(7, 56000, Integer.parseInt(ConstantValues.loginBlockDelayMillis.getDefValue()),
                                Integer.parseInt(ConstantValues.loginMaxBlockDelayMillis.getDefValue()),
                                Integer.parseInt(ConstantValues.maxIncorrectLoginAttempts.getDefValue())),
                        Arguments.of(10, 60000, Integer.parseInt(ConstantValues.loginBlockDelayMillis.getDefValue()),
                                Integer.parseInt(ConstantValues.loginMaxBlockDelayMillis.getDefValue()),
                                Integer.parseInt(ConstantValues.maxIncorrectLoginAttempts.getDefValue()))
                ));
    }

    public static Stream<Arguments> userAccessCacheProvider() {
        return Stream.of(
                Arguments.of("user", true, true, false),
                Arguments.of("user", false, true, false),
                Arguments.of("user", true, false, false),
                Arguments.of("user", false, false, true),
                Arguments.of("", true, true, false),
                Arguments.of("", false, true, false),
                Arguments.of("", true, false, false),
                Arguments.of("", false, false, false),
                Arguments.of(ANONYMOUS, true, true, false),
                Arguments.of(ANONYMOUS, false, true, false),
                Arguments.of(ANONYMOUS, true, false, false),
                Arguments.of(ANONYMOUS, false, false, false)
        );
    }

    @ParameterizedTest
    @MethodSource("dataProvider")
    public void testNextLoginTimes(int attempt, int lockout, int loginBlockDelay, int loginMaxDelay, int maxAttempts) {
        UserLockInMemoryService lockInMemoryService = createUserLockService(loginBlockDelay, loginMaxDelay,
                maxAttempts);
        getNextLoginTime(lockInMemoryService, attempt, lockout);
    }

    @ParameterizedTest
    @MethodSource("userAccessCacheProvider")
    public void testShouldUpdateUserAccessCache(String id, boolean userLockPolicyEnabled, boolean isLocked,
            boolean expected) {
        AccessService accessServiceMock = mock(AccessService.class);
        UserLockPolicyConfig userLockPolicyConfig = UserLockPolicyConfig.builder()
                .build();
        when(accessServiceMock.getUserLockPolicyConfig()).thenReturn(userLockPolicyConfig);
        UserLockInMemoryServiceImpl userLockInMemoryService = new UserLockInMemoryServiceImpl(accessServiceMock);
        if (isLocked) {
            userLockInMemoryService.lockUser(id);
        }

        assertEquals(userLockInMemoryService.shouldUpdateUserAccessCache(id, userLockPolicyEnabled),
                expected);
    }

    private UserLockInMemoryServiceImpl createUserLockService(int loginBlockDelay, int loginMaxDelay, int maxAttempts) {
        AccessService accessServiceMock = mock(AccessService.class);
        UserLockPolicyConfig userLockPolicyConfig = UserLockPolicyConfig.builder()
                .baseLoginDelayMillis(loginBlockDelay)
                .maxLoginDelayMillis(loginMaxDelay)
                .maxLoginDelayIncorrectAttempts(maxAttempts)
                .build();
        when(accessServiceMock.getUserLockPolicyConfig()).thenReturn(userLockPolicyConfig);
        return new UserLockInMemoryServiceImpl(accessServiceMock);
    }

    private void getNextLoginTime(UserLockInMemoryService lockInMemoryService, int attempt, int lockoutTime) {
        long now = System.currentTimeMillis();
        long nextLoginTime = lockInMemoryService.getNextLoginTime(attempt, now, "testUser");
        assertThat(nextLoginTime).isEqualTo(lockoutTime == -1 ? -1 : now + lockoutTime);
    }
}