package org.artifactory.quartz.task.history;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;
import java.util.Set;

@Slf4j
@Builder
public record QuartzTaskHistoryInfo(int runId, String externalId, String taskType, long startedDate,
                                    long finishedDate, long lastModifiedDate, Status status, String statusMessage,
                                    boolean deleted, String taskId, String serverId, boolean manualTrigger,
                                    String username) {

    public QuartzTaskHistoryInfo(String externalId, String taskType, Status status, String taskId,
            String serverId, boolean manualTrigger, String username) {
        this(-1, externalId, taskType, System.currentTimeMillis(), -1, System.currentTimeMillis(), status, null,
                false, taskId, serverId, manualTrigger, username);

    }

    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    public static class QuartzTaskHistoryInfoBuilder {
        private long lastModifiedDate = System.currentTimeMillis();
        private long startedDate = System.currentTimeMillis();
        private long finishedDate = -1L;
    }

    public enum Status {

        COMPLETED,
        COMPLETED_WITH_ERRORS,
        FAILED,
        SKIPPED,
        STOPPED,
        STOPPING,
        PAUSING,
        IN_PROGRESS,
        IN_PROGRESS_WITH_ERRORS,
        RESUMING,
        PAUSED,
        PENDING_EXPIRED_CHECK,
        EXPIRED,
        ORPHANED;
        private Set<Status> canTransitionToStatuses;

        static {
            COMPLETED.canTransitionToStatuses = EnumSet.noneOf(Status.class);
            COMPLETED_WITH_ERRORS.canTransitionToStatuses = EnumSet.noneOf(Status.class);
            FAILED.canTransitionToStatuses = EnumSet.noneOf(Status.class);
            SKIPPED.canTransitionToStatuses = EnumSet.noneOf(Status.class);
            STOPPED.canTransitionToStatuses = EnumSet.noneOf(Status.class);
            STOPPING.canTransitionToStatuses = EnumSet.of(STOPPED, STOPPING, PENDING_EXPIRED_CHECK);
            PAUSING.canTransitionToStatuses = EnumSet.of(PAUSED, STOPPING, PAUSING, RESUMING, PENDING_EXPIRED_CHECK);
            IN_PROGRESS_WITH_ERRORS.canTransitionToStatuses =
                    EnumSet.of(COMPLETED_WITH_ERRORS, FAILED, STOPPING, PAUSING, PENDING_EXPIRED_CHECK);
            IN_PROGRESS.canTransitionToStatuses =
                    EnumSet.of(COMPLETED, FAILED, STOPPING, PAUSING, PENDING_EXPIRED_CHECK);
            RESUMING.canTransitionToStatuses =
                    EnumSet.of(IN_PROGRESS, PAUSING, STOPPING, RESUMING, PENDING_EXPIRED_CHECK);
            PAUSED.canTransitionToStatuses = EnumSet.of(RESUMING, STOPPING, PAUSING, PENDING_EXPIRED_CHECK);
            PENDING_EXPIRED_CHECK.canTransitionToStatuses = EnumSet.of(IN_PROGRESS, IN_PROGRESS_WITH_ERRORS);
            EXPIRED.canTransitionToStatuses = EnumSet.noneOf(Status.class);
            ORPHANED.canTransitionToStatuses = EnumSet.noneOf(Status.class);
        }

        public static Set<Status> getRunningStatuses() {
            return EnumSet.of(IN_PROGRESS, IN_PROGRESS_WITH_ERRORS, PAUSED, PAUSING, RESUMING, STOPPING);
        }

        public static Set<Status> getFinalStatuses() {
            return EnumSet.of(COMPLETED, COMPLETED_WITH_ERRORS, SKIPPED, FAILED, STOPPED);
        }

        public static Set<Status> getCompletedStatuses() {
            return EnumSet.of(COMPLETED, COMPLETED_WITH_ERRORS, STOPPED, FAILED);
        }

        public boolean canTransitionTo(Status status) {
            return canTransitionToStatuses.contains(status);
        }
    }
}