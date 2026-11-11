package org.artifactory.system.load.monitor;

/**
 * Stores the result of a single self-diagnostic probe execution.
 *
 * @param latencyMs  round-trip time in milliseconds (equal to timeout value when timed out)
 * @param timedOut   {@code true} if the probe did not receive a response within the configured timeout
 * @param timestamp  epoch millis when the probe completed
 */
public record ProbeResult(long latencyMs, boolean timedOut, long timestamp) {

    static final ProbeResult INITIAL = new ProbeResult(0, false, 0);
}
