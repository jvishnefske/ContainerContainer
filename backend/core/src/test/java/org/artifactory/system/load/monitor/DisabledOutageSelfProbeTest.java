package org.artifactory.system.load.monitor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Amir Avidan
 */
class DisabledOutageSelfProbeTest {

    private final DisabledOutageSelfProbe probe = new DisabledOutageSelfProbe();

    @Test
    void getLatestResultAlwaysReturnsInitial() {
        ProbeResult result = probe.getLatestResult();
        assertFalse(result.timedOut());
        assertEquals(0, result.latencyMs());
        assertEquals(0, result.timestamp());
    }

    @Test
    void startDoesNotThrow() {
        probe.start();
    }

    @Test
    void stopDoesNotThrow() {
        probe.stop();
    }

    @Test
    void getLatestResultRemainsInitialAfterStart() {
        probe.start();
        ProbeResult result = probe.getLatestResult();
        assertFalse(result.timedOut());
        assertEquals(0, result.latencyMs());
    }

    @Test
    void disabledProbeEmitsNoMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        probe.start();
        probe.stop();
        assertTrue(registry.getMeters().isEmpty(),
                "Disabled probe must not register any meters");
    }
}
