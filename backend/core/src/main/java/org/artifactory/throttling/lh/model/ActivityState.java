package org.artifactory.throttling.lh.model;

import lombok.Data;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Represents the state of an activity, including its running count,
 * blocked status, and health status.
 *
 * @author Hussein Alshqerat
 */
@Data
public class ActivityState {

    private final AtomicLong runningActivities;
    private final AtomicBoolean isBlocked;

    /**
     * Default constructor initializes activity state as healthy with zero running activities
     * and timeout set to zero.
     */
    public ActivityState() {
        this.runningActivities = new AtomicLong(0);
        this.isBlocked = new AtomicBoolean(false);
    }
}
