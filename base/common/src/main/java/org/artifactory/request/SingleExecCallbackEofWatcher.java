package org.artifactory.request;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.EofSensorWatcher;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Custom EOF watcher that executes a callback when a file is consumed or the transmission was aborted.
 * See {@link org.apache.http.conn.EofSensorInputStream} for details.
 *
 * @author Oleksandr Legenkiy
 */
@Slf4j
public class SingleExecCallbackEofWatcher implements EofSensorWatcher {

    private final Runnable callback;
    /**
     * Ensure that the callback is executed only once.
     * For example, there may be more than one call to corresponding input stream "close()" method.
     */
    private final AtomicBoolean executed = new AtomicBoolean(false);

    public SingleExecCallbackEofWatcher(Runnable callback) {
        this.callback = callback;
    }

    @Override
    public boolean eofDetected(InputStream wrapped) {
        return executeCallbackOnlyOnce();
    }

    @Override
    public boolean streamClosed(InputStream wrapped) {
        return executeCallbackOnlyOnce();
    }

    @Override
    public boolean streamAbort(InputStream wrapped) {
        return executeCallbackOnlyOnce();
    }

    private boolean executeCallbackOnlyOnce() {
        if (executed.compareAndSet(false, true)) {
            try {
                callback.run();
            } catch (Exception e) {
                log.warn("Failed to execute 'onClose' callback", e);
            }
        }
        return true; // let parent EofSensorStream handle the call to wrapped.close() method
    }
}
