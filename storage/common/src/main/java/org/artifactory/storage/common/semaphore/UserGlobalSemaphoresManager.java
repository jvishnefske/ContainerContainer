package org.artifactory.storage.common.semaphore;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Manages a two-level semaphore mechanism for controlling concurrent query execution.
 * <p>
 * This class implements a dual-layer permit system to prevent both individual users from monopolizing
 * resources and the overall system from being overloaded:
 * <ul>
 *   <li><b>Per-User Level:</b> Limits the number of concurrent queries a single user can execute,
 *       calculated as a percentage of the global limit</li>
 *   <li><b>Global Level:</b> Limits the total number of concurrent queries across all users</li>
 * </ul>
 * <p>
 * <b>Configuration:</b> The manager is initialized with a global limit, a user percentage of that limit,
 * and a timeout value. These values are stored internally and used for all permit acquisitions. The per-user
 * limit is automatically calculated based on the percentage.
 * <p>
 * <b>Acquisition Order:</b> User-level permits are acquired first, followed by global permits. This order
 * prevents a scenario where a user exhausts the global limit without being subject to their individual quota.
 * <p>
 * <b>Shared Timeout:</b> The configured timeout is shared between both semaphore acquisitions. Time spent
 * acquiring the user-level permit reduces the remaining timeout available for the global permit acquisition.
 * If the total time exceeds the timeout, the operation fails and any acquired permits are released.
 * <p>
 * <b>Bypass Mechanism:</b> When the user percentage is 0 or negative, the per-user semaphore is bypassed
 * entirely, allowing only global-level control. This is useful for system operations (e.g., worker threads)
 * that should not be subject to per-user restrictions.
 * <p>
 * <b>Performance Logging:</b> Successful permit acquisitions are logged at DEBUG level for normal operations
 * (under 5 seconds) and at INFO level for slow acquisitions (over 5 seconds) to help identify potential
 * performance issues.
 * <p>
 * <b>Automatic Cleanup:</b> User semaphores are automatically removed from the internal cache when weakly
 * reachable, preventing memory leaks.
 *
 * @author Dvir Twina
 */
@Slf4j
public class UserGlobalSemaphoresManager {
    private final int globalLimit;
    private final int userPercentageOfGlobal;
    private final int perUserLimit;
    private final long timeoutMilliSecs;
    private final String managerName;
    private final Semaphore globalSemaphore;
    private final Cache<String, Semaphore> userSemaphores;

    public UserGlobalSemaphoresManager(int globalLimit, int userPercentageOfGlobal, long timeoutMilliSecs,
            String managerName) {
        this.globalLimit = globalLimit;
        this.userPercentageOfGlobal = userPercentageOfGlobal;
        this.perUserLimit = calculatePerUserLimit();
        this.timeoutMilliSecs = timeoutMilliSecs;
        this.managerName = managerName;
        this.globalSemaphore = new Semaphore(globalLimit, true);
        this.userSemaphores = CacheBuilder.newBuilder().weakValues().build();

        log.info("{}: Initialized UserGlobalSemaphoresManager with globalLimit={}, userPercentageOfGlobal={}%, " +
                        "calculated perUserLimit={}, timeoutMilliSecs={}", managerName, globalLimit,
                userPercentageOfGlobal, perUserLimit, timeoutMilliSecs);
    }

    /**
     * Acquires permits from both per-user and global semaphores for the specified user.
     * <p>
     * This method attempts to acquire permits from the user's individual semaphore first, then from the
     * global semaphore. The acquisition uses the timeout configured during manager initialization, which
     * is shared between both acquisition attempts.
     * <p>
     * If the per-user limit is configured as 0 (via percentage), the user-level semaphore is bypassed,
     * and only the global semaphore is checked.
     * <p>
     * Timing information is logged for successful acquisitions:
     * <ul>
     *   <li>DEBUG level: Acquisitions completed in under 5 seconds</li>
     *   <li>INFO level: Acquisitions that took 5 seconds or more (potential performance issue)</li>
     * </ul>
     *
     * @param user The user identifier for whom to acquire permits
     * @return PermitsHolder object that must be released after use, or null if acquisition failed within the timeout
     * @throws InterruptedException if the thread is interrupted while waiting for permits
     */
    public PermitsHolder tryAcquire(String user) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long remainingMillis = timeoutMilliSecs;

        Semaphore userSemaphore = null;
        if (perUserLimit > 0) {
            try {
                userSemaphore = userSemaphores.get(user, () -> new Semaphore(perUserLimit, true));
            } catch (ExecutionException e) {
                // This should never happen
                throw new IllegalStateException("Failed to create user semaphore", e);
            }

            boolean userAcquired = userSemaphore.tryAcquire(timeoutMilliSecs, TimeUnit.MILLISECONDS);
            if (!userAcquired) {
                log.debug("{}: Failed to acquire per-user semaphore for user: {}", managerName, user);
                return null;
            }

            long elapsedMillis = System.currentTimeMillis() - startTime;
            remainingMillis = timeoutMilliSecs - elapsedMillis;
            if (remainingMillis <= 0) {
                userSemaphore.release();
                log.debug("{}: Timeout expired after acquiring user semaphore for user: {}, " +
                        "and before acquiring global semaphore", managerName, user);
                return null;
            }
        }

        // Try to acquire global permit with remaining timeout
        boolean globalAcquired = globalSemaphore.tryAcquire(remainingMillis, TimeUnit.MILLISECONDS);
        if (!globalAcquired) {
            // Release user permit if it was acquired
            if (userSemaphore != null) {
                userSemaphore.release();
            }
            log.debug("{}: Failed to acquire global semaphore for user: {}", managerName, user);
            return null;
        }

        long totalElapsedMillis = System.currentTimeMillis() - startTime;
        if (totalElapsedMillis > 5000) {
            log.info("{}: Successfully acquired permits for user '{}' after {} ms (exceeded 5 second threshold)",
                    managerName, user, totalElapsedMillis);
        } else {
            log.debug("{}: Successfully acquired permits for user '{}' after {} ms", managerName, user,
                    totalElapsedMillis);
        }

        return new PermitsHolder(globalSemaphore, userSemaphore);
    }

    /**
     * Calculates the per-user limit based on the instance's global limit and configured percentage.
     * <p>
     * The calculation follows these rules:
     * <ul>
     *   <li>If percentage is 100 or above: returns the full global limit (user can consume all permits)</li>
     *   <li>If percentage is 0 or below: returns 0 (bypasses per-user restrictions, only global limit applies)</li>
     *   <li>For percentages 1-99: calculates the percentage of global limit with floor rounding,
     *       guaranteed minimum of 1 to prevent accidental bypass</li>
     * </ul>
     * <p>
     * Examples: 33% of 10 = 3, 5% of 10 = 1 (not 0), 150% of 10 = 10, -10% of 10 = 0.
     *
     * @return The calculated per-user limit (0 to bypass, globalLimit if 100%+, or calculated value with min of 1)
     */
    private int calculatePerUserLimit() {
        if (userPercentageOfGlobal >= 100) {
            return globalLimit;
        }
        if (userPercentageOfGlobal <= 0) {
            return 0;
        }
        int calculated = (int) Math.floor((globalLimit * userPercentageOfGlobal) / 100.0);
        return Math.max(1, calculated);
    }

    /**
     * Wrapper class that holds acquired permits and handles cleanup on release.
     */
    public static class PermitsHolder implements AutoCloseable {
        private final Semaphore globalSemaphore;
        private final Semaphore userSemaphore;
        private boolean released = false;

        private PermitsHolder(Semaphore globalSemaphore, Semaphore userSemaphore) {
            this.globalSemaphore = globalSemaphore;
            this.userSemaphore = userSemaphore;
        }

        public void release() {
            if (!released) {
                // Only release user semaphore if it exists (not bypassed)
                if (userSemaphore != null) {
                    userSemaphore.release();
                }
                if (globalSemaphore != null) {
                    globalSemaphore.release();
                }
                released = true;
            }
        }

        @Override
        public void close() {
            release();
        }
    }
}
