package org.artifactory.concurrent;

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.common.concurrency.ConflictsGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Utilities for safe use of {@link ConflictsGuard}.
 *
 * @author Mor Merhav
 */
@Slf4j
@UtilityClass
public class ConflictsGuardUtils {

    /**
     * Executes an action while holding the given locks. Locks are acquired in sorted order to prevent
     * deadlocks. Each unlock is wrapped in try-catch so that a failure during unlock does not prevent
     * releasing remaining locks.
     * The actions will run only if all locks were successfully acquired. Otherwise, a {@link LockAlreadyTakenException}
     * is thrown. If no lock keys are provided, the action will not be executed. A warn log will be printed.
     *
     * @param guard   the conflicts guard
     * @param keys    the keys to lock; if null or empty, the action runs without acquiring any locks
     * @param timeout timeout per lock acquisition
     * @param unit    time unit for timeout
     * @param action  the action to run while holding the locks
     * @throws LockAlreadyTakenException if any lock could not be acquired (timeout)
     * @throws LockingFailedException    if interrupted while acquiring locks
     */
    public void runWithLocks(ConflictsGuard<String> guard, Set<String> keys, long timeout,
            TimeUnit unit, Runnable action) {
        try {
            runWithLocks(guard, keys, timeout, unit, () -> {
                action.run();
                return null;
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new LockingFailedException("Run with locks failed", e);
        }
    }

    /**
     * Executes a callable while holding the given locks and returns its result. Locks are acquired in sorted order
     * to prevent deadlocks. Each unlock is wrapped in try-catch so that a failure during unlock does not prevent
     * releasing remaining locks.
     * The callable will run only if all locks were successfully acquired. Otherwise, a
     * {@link LockAlreadyTakenException} is thrown. If no lock keys are provided, the action will not be executed. A
     * warn log will be printed.
     *
     * @param guard    the conflicts guard
     * @param keys     the keys to lock; if null or empty, the callable runs without acquiring any locks
     * @param timeout  timeout per lock acquisition
     * @param unit     time unit for timeout
     * @param callable the callable to run while holding the locks.
     * @return the result of the callable. If no lock keys are provided, null is returned.
     */
    @Nullable
    public <T> T runWithLocks(ConflictsGuard<String> guard, Set<String> keys, long timeout,
            TimeUnit unit, Callable<T> callable) {
        if (keys == null || keys.isEmpty()) {
            log.warn("Lock keys are null or empty. Skipping execution", new Exception());
            return null;
        }
        List<String> acquiredLocks = new ArrayList<>();
        try {
            for (String key : keys.stream().sorted().toList()) {
                boolean acquired = guard.tryToLock(key, timeout, unit);
                if (!acquired) {
                    String errMsg = "Lock already taken: '%s'".formatted(key);
                    log.debug(errMsg);
                    throw new LockAlreadyTakenException(errMsg);
                }
                acquiredLocks.add(key);
            }
            return callable.call();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockingFailedException("Interrupted while acquiring locks for: " + keys, e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new LockingFailedException("Run with locks failed", e);
        } finally {
            releaseLocks(guard, acquiredLocks);
        }
    }

    private void releaseLocks(ConflictsGuard<String> guard, List<String> acquiredLocks) {
        for (String key : acquiredLocks) {
            try {
                guard.unlock(key);
            } catch (Exception e) {
                log.warn("Failed to unlock for key '{}'", key, e);
            }
        }
    }
}
