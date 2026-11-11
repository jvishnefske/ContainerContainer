package org.artifactory.event.work;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;

/**
 * @author Uriah Levy
 */
public class StatusProbeImpl implements StatusProbe {
    private static final Logger log = LoggerFactory.getLogger(StatusProbeImpl.class);

    private long intervalTimeMillis;
    private int maxAttempts;
    private ReentrantLock probingLock = new ReentrantLock();
    private String owner;
    private boolean active;

    public StatusProbeImpl(long intervalTimeMillis, int maxAttempts, String owner) {
        this.intervalTimeMillis = intervalTimeMillis;
        this.maxAttempts = maxAttempts;
        this.owner = owner;
        this.active = true;
    }

    @Override
    public void startProbing(ExecutorService executor, Supplier<Boolean> probeFunction, Runnable onSuccess) {
        executor.submit(() -> {
            boolean probeSuccess = runProbeExclusive(probeFunction);
            if (probeSuccess) {
                onSuccess.run();
            }
        });
    }

    /**
     * Runs the probe loop inside the exclusive lock. Returns true if the probe succeeded,
     * false otherwise. The lock is released before returning, ensuring that the caller
     * (and the onSuccess callback) runs with {@link #isProbing()} == false.
     */
    private boolean runProbeExclusive(Supplier<Boolean> probeFunction) {
        Boolean result = locallyExclusive(() -> {
            boolean probeSuccess = false;
            int currentAttempt = 0;
            log.info("Status probe started (Owner: {})", owner);
            while (!probeSuccess && currentAttempt < maxAttempts && active) {
                try {
                    log.debug("Status Probe started. Attempt number {}", currentAttempt);
                    probeSuccess = probeFunction.get();
                    currentAttempt++;
                    if (!probeSuccess) {
                        pause();
                    }
                } catch (Exception e) {
                    currentAttempt++;
                    pause();
                }
            }
            if (active) {
                if (probeSuccess) {
                    log.debug("Status Probe Success (Owner: {}). Attempt number {}", owner, currentAttempt);
                    return true;
                }
                log.error("Status Probe terminated unsuccessfully (Owner: {})", owner);
            }
            return false;
        });
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean isProbing() {
        return probingLock.isLocked();
    }

    @Override
    public void destroy() {
        active = false;
    }

    private void pause() {
        try {
            log.debug("Probing function indicated failure. Waiting for {} seconds before retrying...",
                    intervalTimeMillis);
            sleep(intervalTimeMillis);
        } catch (InterruptedException e) {
            log.warn("Probing function interrupted", e);
            currentThread().interrupt();
            throw new IllegalStateException("Probing function interrupted.", e);
        }
    }

    private <T> T locallyExclusive(Supplier<T> function) {
        try {
            boolean lockAcquired = probingLock.tryLock();
            if (lockAcquired) {
                log.debug("Local probing exclusive lock acquired");
                return function.get();
            }
        } finally {
            if (probingLock.isHeldByCurrentThread()) {
                probingLock.unlock();
                log.debug("Local probing exclusive lock released");
            }
        }
        return null;
    }
}
