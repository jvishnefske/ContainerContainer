package org.artifactory.event;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.cron.CronUtils;
import org.artifactory.event.migration.EventTableRepoKeyMigration;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.queue.services.PersistentQueueCursorInfo;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUser;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.artifactory.storage.db.event.error.service.NodeEventsErrorsStorageService;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.artifactory.storage.db.event.service.NodeEventCursorService;
import org.artifactory.storage.event.EventsService;
import org.jfrog.common.ClockUtils;
import org.jfrog.storage.DbType;
import org.joda.time.DateTime;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.HOURS;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.DISABLED_BY_SYSTEM;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.OUT_OF_SYNC;
import static org.artifactory.storage.fs.service.ConfigsServiceKey.RTFS_QUEUE_STATE_MD_NEGOTIATION;

@Service
public class EventsLogCleanUpService implements ContextCreationListener {

    private static final Logger log = LoggerFactory.getLogger(EventsLogCleanUpService.class);
    public static final String DELETE_MARKER = "DELETE_MARKER";

    private final TaskService taskService;

    private final EventsService eventsService;

    private final NodeEventCursorService nodeEventCursorService;

    private final NodeEventsErrorsStorageService eventErrorsService;

    private final EventTableRepoKeyMigration eventTableRepoKeyMigration;
    private final FederatedRepoService federatedRepoService;

    @Autowired
    public EventsLogCleanUpService(TaskService taskService, EventsService eventsService,
            NodeEventCursorService nodeEventCursorService, NodeEventsErrorsStorageService eventErrorsService,
            EventTableRepoKeyMigration eventTableRepoKeyMigration, FederatedRepoService federatedRepoService) {
        this.taskService = taskService;
        this.eventsService = eventsService;
        this.nodeEventCursorService = nodeEventCursorService;
        this.eventErrorsService = eventErrorsService;
        this.eventTableRepoKeyMigration = eventTableRepoKeyMigration;
        this.federatedRepoService = federatedRepoService;
    }

    @Override
    public void onContextCreated() {
        Task repeatingTask = TaskUtils.createRepeatingTask(EventsLogCleanUpTask.class,
                HOURS.toMillis(ConstantValues.eventsLogCleanupTaskPeriodHours.getLong()),
                30000,
                "Cleanup old events from events log");
        String cron = shouldScheduleJob() ? getTmpTruncateTaskCron() : null;
        if (cron != null) {
            Task tmpCleanupTask = TaskUtils.createCronTask(TmpEventLogTruncateJob.class, cron);
            taskService.startTask(tmpCleanupTask, false);
        }

        taskService.startTask(repeatingTask, false);
    }

    private boolean shouldScheduleJob() {
        return ArtifactoryHome.get().getDBProperties().getDbType().equals(DbType.POSTGRESQL);
    }

    @VisibleForTesting
    String getTmpTruncateTaskCron() {
        String defaultCron = ConstantValues.tmpEventsLogTruncateTaskCron.getDefValue();
        String configuredCron = ConstantValues.tmpEventsLogTruncateTaskCron.getString();
        if (StringUtils.isBlank(configuredCron)) {
            log.warn("Configured cron for node_events_tmp partition truncate is blank, not scheduling");
            return null;
        }

        if (!CronUtils.isValid(configuredCron)) {
            log.warn("Configured cron for node_events_tmp partition truncate is invalid, using: {}", defaultCron);
            return defaultCron;
        }
        return configuredCron;
    }

    public void cleanup() throws JobExecutionException {
        log.info("Starting cleanup of old events from event log");
        if (eventsService.getEventsCount() == 0) {
            log.info("No events to cleanup in events log");
            return;
        }
        long deleteFrom = eventsService.getFirstEventTimestamp();
        log.info("deleteFrom={}", deleteFrom);
        if (deleteFrom == 0) {
            log.warn("First event is in time 0");
            return;
        }

        long eventsDeleteInterval = getEventsDeleteInterval();
        log.info("eventsDeleteInterval={}", eventsDeleteInterval);

        final MaxBoundary maxBoundary = getMaxBoundary();
        log.info("maxBoundary.timestamp={}", maxBoundary.timestamp);
        log.info("maxBoundary.deleteErrors={}", maxBoundary.shouldDeleteExpiredEvents);

        // Check if errors deletion is required
        if (maxBoundary.isShouldDeleteExpiredEvents() && deleteFrom < maxBoundary.getTimestamp()) {
            //Delete errors first: to avoid integrity violation of events
            long deleted = eventErrorsService.deleteBeforeWithEventId(maxBoundary.getTimestamp());
            log.info("{} errors deleted", deleted);

            // We want to make this operation only on full clean up because
            // otherwise, we cannot be on data loss
            markCursorsAsOutOfSyncIfNeeded(deleteFrom, maxBoundary.getTimestamp());
        }

        // Pagination deleted in bulks events by timestamp. pagination side limitation is bound the user config (the
        // default 4 hours bulks - eventsCleanupIntervalHours)
        // untill it reached the maxBoundary (the last event cursor\event error or 60 days -
        // ageOfEventsLogEntriesToDiscardDays or the first )
        while (deleteFrom < maxBoundary.getTimestamp()) {
            final long deleteTo = min(deleteFrom + eventsDeleteInterval, maxBoundary.getTimestamp());
            log.trace("Deleting events from {} to {}", deleteFrom, deleteTo);
            //Delete Events
            eventsService.deleteRange(deleteFrom, deleteTo);
            nodeEventCursorService.updateOrInsertCursor(
                    new NodeEventCursor(DELETE_MARKER, deleteTo, NodeEventCursorType.DELETE_MARKER));
            deleteFrom = deleteTo;
            sleep();
        }
        log.info("Cleanup of old events from event log finished");
    }

    @VisibleForTesting
    long getEventsDeleteInterval() {
        String propValue = ConstantValues.eventsCleanupIntervalHours.getString();
        try {
            return (long) (Double.parseDouble(propValue) * 3600 * 1000);
        } catch (NumberFormatException e) {
            log.warn("Invalid value supplied for {} - '{}'. Using default of 4.",
                    ConstantValues.eventsCleanupIntervalHours.getPropertyName(), propValue);
            return 4 * 3600 * 1000;
        }

    }

    // will mark the federated event as out_of_sync
    // if the clean up job has removed it's old events
    @VisibleForTesting
    void markCursorsAsOutOfSyncIfNeeded(long deleteFrom, long deleteTo) {
        // Get all cursors that works by 'event id'
        // that that didnt got updated since 'delete to'
        // Set cursor as out of sync
        markAsOutOfSyncIfNeeded(deleteTo, getCursorCandidates(deleteTo),
                cursorInfo ->
                {
                    log.info("Event cleanup removed events for cursor '{}', updating cursor status to {}",
                            cursorInfo.getCursorName(), OUT_OF_SYNC);
                    nodeEventCursorService.updateCursorWithStatus(cursorInfo.getCursorName(),
                            NodeEventCursorStatus.OUT_OF_SYNC);
                });

        // handle DISABLE_BY_SYSTEM cursors differently
        List<NodeEventCursor> disabledBySystemCursors =
                nodeEventCursorService.getEventIdCursorsBy(DISABLED_BY_SYSTEM, deleteTo);
        markAsOutOfSyncIfNeeded(deleteTo, disabledBySystemCursors,
                cursorInfo ->
                {
                    log.info("Event cleanup removed events for {} cursor '{}', updating '{}' configuration to {}",
                            DISABLED_BY_SYSTEM, cursorInfo.getCursorName(),
                            RTFS_QUEUE_STATE_MD_NEGOTIATION.getKeyName(), OUT_OF_SYNC);
                    federatedRepoService.addOrUpdateConfig(RTFS_QUEUE_STATE_MD_NEGOTIATION,
                            NodeEventCursorStatus.OUT_OF_SYNC.name());
                }
        );
    }

    private void markAsOutOfSyncIfNeeded(long deleteTo, List<NodeEventCursor> cursors,
            Consumer<PersistentQueueCursorInfo> markOutOfSync) {
        cursors.stream().map(PersistentQueueCursorInfo::new)
                .filter(cursorInfo -> Objects.nonNull(cursorInfo.getCursorName()))
                .filter(cursorInfo -> checkCursorsRemovedEvents(cursorInfo, deleteTo))
                .forEach(markOutOfSync);
    }

    @Nonnull
    private List<NodeEventCursor> getCursorCandidates(long deleteTo) {
        List<NodeEventCursor> cursors =
                new ArrayList<>(nodeEventCursorService.getEventIdCursorsBy(NodeEventCursorStatus.HEALTHY, deleteTo));
        cursors.addAll(nodeEventCursorService.getEventIdCursorsBy(NodeEventCursorStatus.DISABLED, deleteTo));
        cursors.addAll(nodeEventCursorService.getEventIdCursorsBy(NodeEventCursorStatus.EXHAUSTED, deleteTo));
        cursors.addAll(nodeEventCursorService.getEventIdCursorsBy(NodeEventCursorStatus.PENDING_FS, deleteTo));
        return cursors;
    }

    private boolean checkCursorsRemovedEvents(@Nonnull PersistentQueueCursorInfo cursorInfo, long deleteTo) {
        int cursorDeletedEventsNumber =
                eventsService.countEventsRange(0, deleteTo, cursorInfo.getRepoKey(),
                        cursorInfo.getType().name());
        boolean hasEventsAfterTimestamp = eventsService.hasEventsAfterTimestamp(deleteTo, cursorInfo.getRepoKey());
        if (cursorDeletedEventsNumber > 0 && hasEventsAfterTimestamp) {
            log.info("number of events that going to be deleted for repo key: {} is {}",
                    cursorInfo.getRepoKey(),
                    cursorDeletedEventsNumber);
            return true;
        } else {
            log.debug("number of events in range from {} to {} for repo key: {} is '0'",
                    0,
                    deleteTo,
                    cursorInfo.getRepoKey());
            return false;
        }
    }

    @VisibleForTesting
    MaxBoundary getMaxBoundary() {
        long maxBoundaryFromConfig = getConfigBoundary(new Date(ClockUtils.epochMillis()));
        log.debug("maxBoundaryFromConfig={}", maxBoundaryFromConfig);
        long lastTimestampFromMarker = nodeEventCursorService.oldestTimestampForEventLogOperator();
        log.debug("lastTimestampFromMarker={}", lastTimestampFromMarker);
        long firstEventErrorTimestamp = eventErrorsService.getFirstEventErrorTimestampWithEventId();
        log.debug("firstEventErrorTimestamp={}", firstEventErrorTimestamp);
        long minTimestamp;
        if (lastTimestampFromMarker > 0 && firstEventErrorTimestamp > 0) {
            minTimestamp = min(lastTimestampFromMarker, firstEventErrorTimestamp);
        } else if (lastTimestampFromMarker > 0) {
            minTimestamp = lastTimestampFromMarker;
        } else {
            minTimestamp = firstEventErrorTimestamp;
        }
        log.debug("minTimestamp={}", minTimestamp);
        long eventsMaxBoundary = max(maxBoundaryFromConfig, minTimestamp);
        log.debug("eventsMaxBoundary={}", eventsMaxBoundary);
        return MaxBoundary.builder()
                .timestamp(eventsMaxBoundary)
                .shouldDeleteExpiredEvents(maxBoundaryFromConfig == eventsMaxBoundary)
                .build();
    }


    @VisibleForTesting
    long getConfigBoundary(Date time) {
        return new DateTime(time.getTime())
                .minusDays(ConstantValues.ageOfEventsLogEntriesToDiscardDays.getInt()).getMillis();
    }

    private void sleep() {
        try {
            Thread.sleep(ConstantValues.eventsCleanupIterationSleepTimeMillis.getLong());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventsCleanupException("Error while cleaning up events log", e);
        }
    }

    @JobCommand(jobName = JobName.EVENT_LOG_CLEANUP, singleton = true,
            schedulerUser = TaskUser.SYSTEM,
            manualUser = TaskUser.SYSTEM,
            description = "Cleanup old events from events log")
    public static class EventsLogCleanUpTask extends QuartzCommand {

        @Override
        protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
            ContextHelper.get().beanForType(EventsLogCleanUpService.class).cleanup();
            ContextHelper.get().beanForType(EventTableRepoKeyMigration.class).eventTableRepoKeyMigrationRetryManager();
        }
    }

    @VisibleForTesting
    @Data
    @Builder
    static class MaxBoundary {
        private long timestamp;
        private boolean shouldDeleteExpiredEvents;
    }
}
