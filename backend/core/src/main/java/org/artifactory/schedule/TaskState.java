package org.artifactory.schedule;

import org.artifactory.concurrent.State;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Mor Merhav
 */
public enum TaskState implements State {
    VIRGIN,
    SCHEDULED,
    RUNNING,
    PAUSING,
    STOPPING,
    STOPPED, //Will not start if refired by the scheduler
    PAUSED, //Blocked by executions thread (and will not start if refired by scheduler)
    CANCELED;

    @SuppressWarnings({"OverlyComplexMethod"})
    private static Set<TaskState> getPossibleTransitionStates(TaskState oldState) {
        HashSet<TaskState> states = new HashSet<>();
        switch (oldState) {
            case VIRGIN:
                states.add(TaskState.SCHEDULED);
                states.add(TaskState.CANCELED);
                return states;
            case SCHEDULED:
                states.add(TaskState.RUNNING);
                states.add(TaskState.PAUSING);
                states.add(TaskState.STOPPING);
                states.add(TaskState.STOPPED);
                states.add(TaskState.CANCELED);
                return states;
            case RUNNING:
                states.add(TaskState.PAUSING);
                states.add(TaskState.STOPPING);
                states.add(TaskState.STOPPED);
                states.add(TaskState.CANCELED);
                states.add(TaskState.SCHEDULED);
                return states;
            case PAUSING:
                states.add(TaskState.PAUSED);
                states.add(TaskState.RUNNING);
                states.add(TaskState.STOPPING);
                states.add(TaskState.STOPPED);
                states.add(TaskState.CANCELED);
                return states;
            case PAUSED:
                states.add(TaskState.RUNNING);
                states.add(TaskState.STOPPING);
                states.add(TaskState.STOPPED);
                states.add(TaskState.CANCELED);
                return states;
            case STOPPING:
            case STOPPED:
                states.add(TaskState.STOPPED);
                states.add(TaskState.RUNNING);
                states.add(TaskState.SCHEDULED);
                states.add(TaskState.CANCELED);
                return states;
            case CANCELED:
                //Unscheduled
                return states;
            default:
                throw new IllegalArgumentException(
                        "No transitions defined for state: " + oldState);
        }
    }

    @Override
    @SuppressWarnings({"SuspiciousMethodCalls"})
    public boolean canTransitionTo(State newState) {
        Set<TaskState> states = getPossibleTransitionStates(this);
        return states.contains(newState);
    }
}
