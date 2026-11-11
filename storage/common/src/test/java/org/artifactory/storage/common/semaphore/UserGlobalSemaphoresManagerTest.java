package org.artifactory.storage.common.semaphore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Dvir Twina
 */
class UserGlobalSemaphoresManagerTest {

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @ParameterizedTest
    @MethodSource("acquisitionScenarios")
    void testPermitAcquisition(int globalLimit, int userPercentage, String description) throws Exception {
        UserGlobalSemaphoresManager manager =
                new UserGlobalSemaphoresManager(globalLimit, userPercentage, 1000, "test");
        String user = "testUser";

        UserGlobalSemaphoresManager.PermitsHolder permits = manager.tryAcquire(user);

        assertNotNull(permits, "Should successfully acquire permits: " + description);

        Semaphore globalSemaphore = getGlobalSemaphore(permits);
        assertNotNull(globalSemaphore);
        assertEquals(globalLimit - 1, globalSemaphore.availablePermits(),
                "Global semaphore should have one less permit");

        permits.release();
        assertEquals(globalLimit, globalSemaphore.availablePermits(), "Global permits should be returned");
    }

    private static Stream<Arguments> acquisitionScenarios() {
        return Stream.of(
                Arguments.of(5, 60, "Normal acquisition with both user and global permits"),
                Arguments.of(5, 0, "Bypass user-level when userPercentage is zero")
        );
    }

    @Test
    void testPerUserLimitEnforcement() throws Exception {
        int globalLimit = 10;
        int userPercentage = 20; // perUserLimit will be 2
        UserGlobalSemaphoresManager manager = new UserGlobalSemaphoresManager(globalLimit, userPercentage, 200, "test");
        String user = "testUser";

        UserGlobalSemaphoresManager.PermitsHolder permit1 = manager.tryAcquire(user);
        UserGlobalSemaphoresManager.PermitsHolder permit2 = manager.tryAcquire(user);
        UserGlobalSemaphoresManager.PermitsHolder permit3 = manager.tryAcquire(user);

        assertNotNull(permit1, "First permit should be acquired");
        assertNotNull(permit2, "Second permit should be acquired");
        assertNull(permit3, "Third permit should fail due to per-user limit");

        Semaphore userSemaphore = getUserSemaphore(permit1);
        assertNotNull(userSemaphore);
        assertEquals(0, userSemaphore.availablePermits(), "User semaphore should be exhausted");

        permit1.release();
        permit2.release();
    }

    @Test
    void testGlobalLimitEnforcement() throws Exception {
        UserGlobalSemaphoresManager manager = new UserGlobalSemaphoresManager(2, 100, 200, "test");
        String user1 = "user1";
        String user2 = "user2";

        UserGlobalSemaphoresManager.PermitsHolder permit1 = manager.tryAcquire(user1);
        UserGlobalSemaphoresManager.PermitsHolder permit2 = manager.tryAcquire(user2);
        UserGlobalSemaphoresManager.PermitsHolder permit3 = manager.tryAcquire(user1);

        assertNotNull(permit1, "First user should acquire permit");
        assertNotNull(permit2, "Second user should acquire permit");
        assertNull(permit3, "Third acquisition should fail due to global limit");

        Semaphore globalSemaphore = getGlobalSemaphore(permit1);
        assertEquals(0, globalSemaphore.availablePermits(), "Global semaphore should be exhausted");

        permit1.release();
        permit2.release();
    }

    @Test
    void testTimeoutBehavior_whenUserPermitNotAvailable() throws Exception {
        UserGlobalSemaphoresManager manager = new UserGlobalSemaphoresManager(10, 10, 50, "test");
        String user = "testUser";

        UserGlobalSemaphoresManager.PermitsHolder permit1 = manager.tryAcquire(user);
        assertNotNull(permit1);

        long startTime = System.currentTimeMillis();
        UserGlobalSemaphoresManager.PermitsHolder permit2 = manager.tryAcquire(user);
        long elapsedTime = System.currentTimeMillis() - startTime;

        assertNull(permit2, "Should timeout waiting for user permit");
        assertTrue(elapsedTime >= 50, "Should wait at least the timeout duration");

        permit1.release();
    }

    @Test
    void testSharedTimeout_betweenUserAndGlobalAcquisition() throws Exception {
        UserGlobalSemaphoresManager manager = new UserGlobalSemaphoresManager(10, 30, 50, "test");
        String user = "testUser";

        UserGlobalSemaphoresManager.PermitsHolder[] permits = new UserGlobalSemaphoresManager.PermitsHolder[10];
        int acquired = 0;
        int attempts = 0;
        while (acquired < 10 && attempts < 20) {
            UserGlobalSemaphoresManager.PermitsHolder permit = manager.tryAcquire("user" + acquired);
            if (permit != null) {
                permits[acquired] = permit;
                acquired++;
            }
            attempts++;
        }
        assertEquals(10, acquired, "Setup should acquire all 10 permits");

        long startTime = System.currentTimeMillis();
        UserGlobalSemaphoresManager.PermitsHolder permit = manager.tryAcquire(user);
        long elapsedTime = System.currentTimeMillis() - startTime;

        assertNull(permit, "Should fail to acquire global permit");
        assertTrue(elapsedTime >= 50, "Should use the full timeout");

        for (UserGlobalSemaphoresManager.PermitsHolder p : permits) {
            if (p != null) {p.release();}
        }
    }

    @Test
    void testMultipleUsersIndependentLimits() throws Exception {
        UserGlobalSemaphoresManager manager = new UserGlobalSemaphoresManager(10, 20, 200, "test");
        String user1 = "user1";
        String user2 = "user2";

        UserGlobalSemaphoresManager.PermitsHolder user1Permit1 = manager.tryAcquire(user1);
        UserGlobalSemaphoresManager.PermitsHolder user1Permit2 = manager.tryAcquire(user1);
        UserGlobalSemaphoresManager.PermitsHolder user2Permit1 = manager.tryAcquire(user2);
        UserGlobalSemaphoresManager.PermitsHolder user2Permit2 = manager.tryAcquire(user2);
        UserGlobalSemaphoresManager.PermitsHolder user1Permit3 = manager.tryAcquire(user1);
        UserGlobalSemaphoresManager.PermitsHolder user2Permit3 = manager.tryAcquire(user2);

        assertNotNull(user1Permit1);
        assertNotNull(user1Permit2);
        assertNotNull(user2Permit1);
        assertNotNull(user2Permit2);
        assertNull(user1Permit3, "User1 should be at their limit (2 permits)");
        assertNull(user2Permit3, "User2 should be at their limit (2 permits)");

        user1Permit1.release();
        user1Permit2.release();
        user2Permit1.release();
        user2Permit2.release();
    }

    @Test
    void testReleaseIdempotence() throws Exception {
        UserGlobalSemaphoresManager manager = new UserGlobalSemaphoresManager(5, 60, 500, "test");
        String user = "testUser";

        UserGlobalSemaphoresManager.PermitsHolder permits = manager.tryAcquire(user);
        assertNotNull(permits);

        Semaphore globalSemaphore = getGlobalSemaphore(permits);
        int availableBeforeRelease = globalSemaphore.availablePermits();

        permits.release();
        permits.release();
        permits.release();

        assertEquals(availableBeforeRelease + 1, globalSemaphore.availablePermits(),
                "Multiple releases should only release once");

        UserGlobalSemaphoresManager.PermitsHolder permits2 = manager.tryAcquire(user);
        assertNotNull(permits2, "Should be able to acquire after idempotent release");
        permits2.release();
    }

    @Test
    void testAutoCloseableInterface() throws Exception {
        UserGlobalSemaphoresManager manager = new UserGlobalSemaphoresManager(5, 40, 200, "test");
        String user = "testUser";

        UserGlobalSemaphoresManager.PermitsHolder permit3;
        try (UserGlobalSemaphoresManager.PermitsHolder permits1 = manager.tryAcquire(user);
             UserGlobalSemaphoresManager.PermitsHolder permits2 = manager.tryAcquire(user)) {
            assertNotNull(permits1);
            assertNotNull(permits2);
            permit3 = manager.tryAcquire(user);
            assertNull(permit3, "Should not be able to acquire third permit");
        }

        permit3 = manager.tryAcquire(user);
        assertNotNull(permit3, "Permits should be released via AutoCloseable");
        permit3.release();
    }

    @Test
    void testConcurrentAccess_multipleThreads() throws InterruptedException {
        UserGlobalSemaphoresManager manager = new UserGlobalSemaphoresManager(10, 30, 100, "test");
        String user = "testUser";
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    UserGlobalSemaphoresManager.PermitsHolder permits = manager.tryAcquire(user);
                    if (permits != null) {
                        successCount.incrementAndGet();
                        Thread.sleep(200);
                        permits.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        completeLatch.await(5, TimeUnit.SECONDS);

        assertEquals(3, successCount.get(),
                "Only perUserLimit (3) threads should successfully acquire permits");
    }

    @Test
    void testBypassUserLevelWhenPercentageIsZero() throws Exception {
        UserGlobalSemaphoresManager manager = new UserGlobalSemaphoresManager(5, 0, 1000, "test");
        String user = "testUser";

        UserGlobalSemaphoresManager.PermitsHolder permits = manager.tryAcquire(user);
        assertNotNull(permits);

        // Verify user semaphore is null (bypassed)
        Semaphore userSemaphore = getUserSemaphore(permits);
        assertNull(userSemaphore, "User semaphore should be null when percentage is 0");

        permits.release();
    }

    @ParameterizedTest
    @MethodSource("calculatePerUserLimitScenarios")
    void testCalculatePerUserLimit(int globalLimit, int percentage, int expectedPerUserLimit, String description)
            throws Exception {
        UserGlobalSemaphoresManager manager = new UserGlobalSemaphoresManager(globalLimit, percentage, 1000, "test");
        int perUserLimit = getPerUserLimit(manager);
        assertEquals(expectedPerUserLimit, perUserLimit, description);
    }

    private static Stream<Arguments> calculatePerUserLimitScenarios() {
        return Stream.of(
                // Bypass scenarios (percentage <= 0)
                Arguments.of(10, 0, 0, "Percentage 0 should result in perUserLimit 0 (bypass)"),
                Arguments.of(10, -10, 0, "Negative percentage should result in perUserLimit 0 (bypass)"),

                // Full access scenarios (percentage >= 100)
                Arguments.of(10, 100, 10, "Percentage 100 should equal globalLimit"),
                Arguments.of(10, 150, 10, "Percentage above 100 should cap at globalLimit"),

                // Standard percentage calculations with floor rounding
                Arguments.of(10, 33, 3, "33% of 10 should be 3 (floor from 3.3)"),
                Arguments.of(10, 25, 2, "25% of 10 should be 2 (floor from 2.5)"),
                Arguments.of(7, 28, 1, "28% of 7 should be 1 (floor from 1.96)"),
                Arguments.of(10, 20, 2, "20% of 10 should be 2"),

                // Minimum enforcement (low percentages that would floor to 0)
                Arguments.of(10, 5, 1, "5% of 10 should be 1 (minimum enforcement, not 0)"),
                Arguments.of(100, 1, 1, "1% of 100 should be 1 (minimum enforcement)")
        );
    }

    private Semaphore getGlobalSemaphore(UserGlobalSemaphoresManager.PermitsHolder permits) throws Exception {
        Field field = permits.getClass().getDeclaredField("globalSemaphore");
        field.setAccessible(true);
        return (Semaphore) field.get(permits);
    }

    private Semaphore getUserSemaphore(UserGlobalSemaphoresManager.PermitsHolder permits) throws Exception {
        Field field = permits.getClass().getDeclaredField("userSemaphore");
        field.setAccessible(true);
        return (Semaphore) field.get(permits);
    }

    private int getPerUserLimit(UserGlobalSemaphoresManager manager) throws Exception {
        Field field = manager.getClass().getDeclaredField("perUserLimit");
        field.setAccessible(true);
        return (int) field.get(manager);
    }
}
