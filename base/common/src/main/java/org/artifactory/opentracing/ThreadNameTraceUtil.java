package org.artifactory.opentracing;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.MDC;

import jakarta.annotation.Nullable;

import static org.artifactory.opentracing.ArtifactoryTracer.UBER_TRACE_ID;

/**
 * Applying extra trace information on thread name will allow us to have better visibility while taking thread dumps
 * .<br>
 * the 'now' represent the time the request has started which allow you to see how much time a running/stuck thread
 * is being running while It's still alive
 * trace-id helps with traceability to other logs with the same trace (when looking at support bundles)
 * <br>e.g:
 * thread name "federated-binary-producer" becomes:<br>
 * "2022-06-15T16:41:33.882+03:00|a6ea22f5c300e0b|federated-binary-producer"
 * <p>
 * and thread dump will look like that<br>
 * <pre>
 * "2022-06-15T16:41:33.882+03:00|a6ea22f5c300e0b|federated-binary-producer"@1924 daemon prio=5 tid=0x404 nid=NA waiting
 * java.lang.Thread.State: WAITING
 * at jdk.internal.misc.Unsafe.park(Unsafe.java:-1)
 * at java.util.concurrent.locks.LockSupport.parkNanos(LockSupport.java:234)
 * at org.artifactory.addon.federated.binary.PersistedTaskProducer.run(PersistedTaskProducer.java:66)
 * </pre>
 * @author avishayh
 */
public class ThreadNameTraceUtil {

    private static final String SEPARATOR = "|";

    public static void enhanceThreadName() {
        enhanceThreadName(null);
    }

    /**
     * @param extraInformation provide stuff like uri to be displayed in the thread name
     */
    public static void enhanceThreadName(@Nullable String extraInformation) {
        String traceId = MDC.get(UBER_TRACE_ID);
        DateTime now = DateTime.now();
        String originalName = Thread.currentThread().getName();
        String extended = StringUtils.isNotEmpty(extraInformation) ? String.format("|%s", extraInformation) : "";
        String newThreadName = String.format("%s|%s%s|%s", now, traceId, extended, originalName);
        Thread.currentThread().setName(newThreadName);
    }

    public static String getOriginalThreadName() {
        String name = Thread.currentThread().getName();
        if (name.contains(SEPARATOR)) {
            return StringUtils.substringAfterLast(name, SEPARATOR);
        }
        return name;
    }

}
