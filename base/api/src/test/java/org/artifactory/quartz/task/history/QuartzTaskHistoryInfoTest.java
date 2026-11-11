package org.artifactory.quartz.task.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.COMPLETED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.COMPLETED_WITH_ERRORS;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.EXPIRED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.FAILED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.IN_PROGRESS;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.IN_PROGRESS_WITH_ERRORS;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.ORPHANED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.PAUSED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.PAUSING;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.PENDING_EXPIRED_CHECK;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.RESUMING;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.SKIPPED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.STOPPED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.STOPPING;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QuartzTaskHistoryInfoTest {

    @Test
    void testDoNotForgetToUpdateCanTransitionToStatusesMethodAfterAddingStatusIfNeeded() {
        assertEquals(14, Status.values().length);
    }

    @Test
    void testGetCompletedCodes() {
        Set<Status> completedStatuses = Status.getCompletedStatuses();
        assertEquals(EnumSet.of(COMPLETED, COMPLETED_WITH_ERRORS, STOPPED, FAILED), completedStatuses);
    }

    @Test
    void testGetFinalStatuses() {
        Set<Status> finalStatuses = Status.getFinalStatuses();
        assertEquals(EnumSet.of(COMPLETED, COMPLETED_WITH_ERRORS, SKIPPED, FAILED, STOPPED), finalStatuses);
    }

    @Test
    void getRunningStatuses() {
        Set<Status> runningStatuses = Status.getRunningStatuses();
        assertEquals(Set.of(IN_PROGRESS, IN_PROGRESS_WITH_ERRORS, PAUSED, PAUSING, RESUMING, STOPPING),
                runningStatuses);
    }

    @ParameterizedTest
    @MethodSource("getExpectedTransitions")
    void canTransitionTo(Status status, Set<Status> canTransitionToStatuses) {
        for (Status transitionToStatus : Status.values()) {
            boolean expected = canTransitionToStatuses.contains(transitionToStatus);
            boolean actual = status.canTransitionTo(transitionToStatus);
            assertEquals(expected, actual,
                    String.format("Expected %s.canTransitionTo(%s) to be: %b but was %b", status, transitionToStatus,
                            expected, actual));
        }

    }

    static Stream<Arguments> getExpectedTransitions() {
        return Stream.of(
                Arguments.of(COMPLETED, EnumSet.noneOf(Status.class)),
                Arguments.of(FAILED, EnumSet.noneOf(Status.class)),
                Arguments.of(SKIPPED, EnumSet.noneOf(Status.class)),
                Arguments.of(STOPPED, EnumSet.noneOf(Status.class)),
                Arguments.of(STOPPING, EnumSet.of(STOPPED, STOPPING, PENDING_EXPIRED_CHECK)),
                Arguments.of(PAUSING, EnumSet.of(PAUSED, STOPPING, PAUSING, RESUMING, PENDING_EXPIRED_CHECK)),
                Arguments.of(IN_PROGRESS, EnumSet.of(COMPLETED, FAILED, STOPPING, PAUSING, PENDING_EXPIRED_CHECK)),
                Arguments.of(IN_PROGRESS_WITH_ERRORS,
                        EnumSet.of(COMPLETED_WITH_ERRORS, FAILED, STOPPING, PAUSING, PENDING_EXPIRED_CHECK)),
                Arguments.of(RESUMING, EnumSet.of(IN_PROGRESS, PAUSING, STOPPING, RESUMING, PENDING_EXPIRED_CHECK)),
                Arguments.of(PAUSED, EnumSet.of(RESUMING, STOPPING, PAUSING, PENDING_EXPIRED_CHECK)),
                Arguments.of(PENDING_EXPIRED_CHECK, EnumSet.of(IN_PROGRESS, IN_PROGRESS_WITH_ERRORS)),
                Arguments.of(EXPIRED, EnumSet.noneOf(Status.class)),
                Arguments.of(ORPHANED, EnumSet.noneOf(Status.class)));
    }
}