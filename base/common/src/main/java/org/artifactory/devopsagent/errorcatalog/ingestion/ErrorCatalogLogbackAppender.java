package org.artifactory.devopsagent.errorcatalog.ingestion;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import org.artifactory.common.ConstantValues;
import org.artifactory.devopsagent.errorcatalog.ErrorCatalogEvent;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * Logback appender that captures ERROR/WARN events into the {@link ErrorCatalogEventBuffer}
 * for asynchronous flush to {@code error_catalog_templates} + {@code error_catalog}.
 *
 * <p><strong>Wiring:</strong> the appender is operator-opt-in. Add the snippet below to
 * {@code logback.xml} on the JPD to enable it. The runtime feature flag
 * {@code devops-agent.troubleshooting.enabled} (default {@code false}) is the secondary gate.</p>
 *
 * <pre>{@code
 *   <appender name="ERROR_CATALOG"
 *             class="org.artifactory.devopsagent.errorcatalog.ingestion.ErrorCatalogLogbackAppender"/>
 *   <root>
 *     <appender-ref ref="ERROR_CATALOG"/>
 *   </root>
 * }</pre>
 *
 * <p><strong>Threading:</strong> all work happens on the logging thread. There is no DB I/O,
 * no blocking, and no exceptions propagate out of {@link #append(ILoggingEvent)}. Templating,
 * sanitization, and signature hashing run inline; the result is handed to a non-blocking buffer
 * for asynchronous flush.</p>
 */
public class ErrorCatalogLogbackAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final AtomicReference<ErrorCatalogEventBuffer> BUFFER = new AtomicReference<>();

    /** Counts events accepted before a buffer is wired — exposed for observability. */
    private static final LongAdder DROPPED_NO_BUFFER = new LongAdder();

    /**
     * Wires the singleton buffer the service manages. Called once from
     * {@code ErrorCatalogService#@PostConstruct} before scheduling the flush job.
     */
    public static void installBuffer(ErrorCatalogEventBuffer newBuffer) {
        BUFFER.set(newBuffer);
    }

    /** Test-only: unset the buffer. */
    public static void uninstallBuffer() {
        BUFFER.set(null);
    }

    /** Test/monitoring hook: events dropped because no buffer is yet installed. */
    public static long droppedNoBufferCount() {
        return DROPPED_NO_BUFFER.sum();
    }

    @Override
    protected void append(ILoggingEvent event) {
        try {
            if (!isCapturableLevel(event.getLevel())) {
                return;
            }
            if (!ConstantValues.devopsTroubleshootingEnabled.getBoolean()) {
                return;
            }
            ErrorCatalogEventBuffer target = BUFFER.get();
            if (target == null) {
                DROPPED_NO_BUFFER.increment();
                return;
            }
            ErrorCatalogEvent ewl = SignatureExtractor.extract(event);
            target.offer(ewl);
        } catch (RuntimeException ex) {
            // Logging-thread safety: never bubble an error out of the appender path.
            addWarn("ErrorCatalogLogbackAppender failed to capture an event", ex);
        }
    }

    private static boolean isCapturableLevel(Level level) {
        return Level.ERROR.equals(level) || Level.WARN.equals(level);
    }
}
