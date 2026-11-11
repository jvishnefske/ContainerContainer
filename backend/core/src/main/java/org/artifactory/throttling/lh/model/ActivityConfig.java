package org.artifactory.throttling.lh.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Activity config model that represents the activity name, min and max thresholds, and healthy timeout.
 */
@Data
@AllArgsConstructor
@Slf4j
public class ActivityConfig {
    private final String name;
    private final long minThreshold;
    private final long maxThreshold;
    private final long healthyTimeout;
    private ReactiveActivityState reactiveState;

    public String name() {
        return name;
    }

    public long minThreshold() {
        return minThreshold;
    }

    public long maxThreshold() {
        return maxThreshold;
    }

    public long healthyTimeout() {
        return healthyTimeout;
    }

    public synchronized ReactiveActivityState reactiveState() {
        return reactiveState;
    }

    public synchronized void setReactiveState(ReactiveActivityState reactiveState) {
        log.info("Setting activity {} to {} reactive state", name, reactiveState);
        this.reactiveState = reactiveState;
    }
}
