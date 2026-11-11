/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.event.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.annotation.Nonnull;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.event.dao.EventsDao;
import org.artifactory.storage.db.event.entity.EventRecord;
import org.artifactory.storage.db.event.entity.EventRecordHints;
import org.artifactory.storage.db.event.entity.EventsDaoParams;
import org.artifactory.storage.db.init.migration.model.MigrationStatus;
import org.artifactory.storage.db.init.migration.service.MigrationStatusStorageService;
import org.artifactory.storage.event.EventsService;
import org.jfrog.common.ClockUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A business service to interact with the events table.
 *
 * @author Yossi Shaul
 */
@Service
public class EventsServiceImpl implements EventsService, InternalEventsService {
    private static final Logger log = LoggerFactory.getLogger(EventsServiceImpl.class);
    /**
     * Optional file logger to print events to a file. Format of the file is timestamp|type|path
     */
    private static final Logger eventLog = LoggerFactory.getLogger("events");
    private final String MONITOR_MIGRATION_NAME = "event-table-repo-key-migration";
    private EventsDao eventsDao;

    private final List<EventsMetrics> metrics;

    private ReentrantLock shiftEventLock = new ReentrantLock();
    private volatile Boolean monitorMigrationFinished = null;
    private MigrationStatusStorageService migrationStatusStorageService;
    private boolean isEventTableRepoKeyMigrationFinished= false;
    private final LoadingCache<String, Boolean> migrationCache;

    @Autowired
    public void setMigrationStatusStorageService(MigrationStatusStorageService migrationStatusStorageService) {
        this.migrationStatusStorageService = migrationStatusStorageService;
    }

    @Autowired
    public void setEventsDao(EventsDao eventsDao) {
        this.eventsDao = eventsDao;
    }

    /**
     * Constructs an EventsServiceImpl and initializes the migration status cache with a 1-minute expiration time.
     * The cache stores the status of the monitor migration process, loading it when needed from the database.
     */
    @Autowired
    public EventsServiceImpl(List<EventsMetrics> metrics) {
        this.metrics = metrics;
        // Initialize the cache with a 1-minute expiration
        migrationCache = CacheBuilder.newBuilder()
                .expireAfterWrite(
                        Long.parseLong(ConstantValues.eventsRepoMigrationCachedExpiredTimeMinutes.getDefValue()),
                        TimeUnit.MINUTES)
                .build(new CacheLoader<>() {
                    @Nonnull
                    @Override
                    public Boolean load(@Nonnull String key) {
                        if (getMonitorMigrationName().equals(key)) {
                            return checkMigrationStatus();
                        }
                        return false;
                    }
                });
    }

    @Override
    public long getEventsCount() {
        try {
            return eventsDao.getEventsCount();
        } catch (SQLException e) {
            throw new StorageException("Failed to count events", e);
        }
    }

    @Override
    public void appendEvents(List<EventInfo> events) {
        List<EventRecord> records = new ArrayList<>();
        for (EventInfo event : events) {
            log.trace("Appending event {}", event);
            long timestamp = ClockUtils.epochMillis();
            records.add(toEventRecord(event, timestamp));
            for(EventsMetrics metric : metrics) {
                metric.reportEvent(event);
            }
        }
        try {
            eventsDao.createBulk(records);
        } catch (SQLException e) {
            throw new StorageException("Failed to persist events", e);
        }

        logEvents(records);
    }

    @Override
    public List<EventInfo> getEventsAfter(long eventId, String repoKey, String fileSuffix, int limit,
            String cursorType) {
        try {
            List<EventRecord> records;

            if (ConstantValues.eventTableRepoKeyMigrationEnable.getBoolean()
                    && checkIfMonitorMigrationFinishedTotally()) {
                log.debug("Event table repo key migration finished get events after with repo column");
                records = getRecords(eventId, repoKey, fileSuffix, limit, true);
            } else {
                log.debug("Event table repo key migration did not finish get events after without repo column");
                records = getRecords(eventId, repoKey, fileSuffix, limit, false);
            }
            return records.stream().map(this::toEventInfo).collect(Collectors.toList());
        } catch (SQLException | ExecutionException e) {
            throw new StorageException("Failed to retrieve event", e);
        }
    }

    @Override
    public boolean hasEventsAfterTimestamp(long timestamp, String repoKey) {
        try {
            if (ConstantValues.eventTableRepoKeyMigrationEnable.getBoolean()
                    && checkIfMonitorMigrationFinishedTotally()) {
                log.debug("Event table repo key migration finished, " +
                        "calling has events after with repo column");
                return eventsDao.hasEventsAfterTimestamp(timestamp, repoKey, true);
            } else {
                log.debug("Event table repo key migration did not finish, " +
                        "calling has events after without repo column");
                return eventsDao.hasEventsAfterTimestamp(timestamp, repoKey, false);
            }

        } catch (SQLException | ExecutionException e) {
            String errMsg = "Failed to check if has events after timestamp for repo %s. Reason: %s"
                    .formatted(repoKey, e.getMessage());
            throw new StorageException(errMsg.formatted(repoKey), e);
        }
    }

    @Nonnull
    List<EventRecord> getRecords(long eventId, String repoKey, String fileSuffix, int limit,
            boolean withRepoColumn)
            throws SQLException {
        return eventsDao.loadEventsByParams(EventsDaoParams.builder()
                .eventId(eventId)
                .inclusive(false)
                .limit(limit)
                .repoKey(repoKey)
                .suffix(fileSuffix)
                .afterRepoColumnMigration(withRepoColumn)
                .build());
    }

    /**
     * This function is intended to return the count of events occurring after the eventId by repo key and fileSuffix.
     * Be aware of the limitations of this method, as it may potentially return a large number of rows,
     * which could strain the database.
     * @param eventId    The timestamp from to get events after
     * @param repoKey    The repository key of events to get
     * @param fileSuffix The suffix of the file if events to get
     */
    @Override
    public int countEventsAfter(long eventId, String repoKey, String fileSuffix) {
        try {
            if (ConstantValues.eventTableRepoKeyMigrationEnable.getBoolean()
                    && checkIfMonitorMigrationFinishedTotally()) {
                log.debug("Event table repo key migration finished, get count events after with repo column");
                return eventsDao.countEventsByParams(EventsDaoParams.builder()
                        .eventId(eventId)
                        .repoKey(repoKey)
                        .suffix(fileSuffix)
                        .afterRepoColumnMigration(true)
                        .build());
            } else {
                log.debug("Event table repo key migration finished, get count events after without repo column");
                return eventsDao.countEventsByParams(EventsDaoParams.builder()
                        .eventId(eventId)
                        .repoKey(repoKey)
                        .suffix(fileSuffix)
                        .afterRepoColumnMigration(false)
                        .build());
            }
        } catch (SQLException | ExecutionException e) {
            throw new StorageException("Failed to retrieve event", e);
        }
    }

    @Override
    public long getFirstEventTimestamp() {
        try {
            return eventsDao.getFirstEventTimestamp();
        } catch (SQLException e) {
            throw new StorageException("Failed to retrieve first event timestamp", e);
        }
    }

    @Override
    public List<EventInfo> getEventsByTimestamp(long timestamp) {
        try {
            List<EventRecord> records = eventsDao.loadEventsByTimestamp(timestamp);
            return records.stream().map(this::toEventInfo).collect(Collectors.toList());
        } catch (SQLException e) {
            throw new StorageException("Failed to get events by timestamp", e);
        }
    }

    @Override
    public Stream<EventInfo> getEventsSince(long timestamp, long maxEvents) {
        CompletableFuture<SQLException> onException = futureBounceSqlExceptionToStorageException();
        return eventsDao.loadNewerStream(timestamp, maxEvents, onException).map(this::toEventInfo);
    }

    @Override
    public List<EventInfo> getEventsInterval(long from, long to, int maxEvents, String repoKey) {
        CompletableFuture<SQLException> onException = futureBounceSqlExceptionToStorageException();
        try (Stream<EventRecord> eventRecordStream = eventsDao
                .loadIntervalStream(from, to, repoKey, maxEvents, onException)) {
            return eventRecordStream
                    .map(this::toEventInfo)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public long getLastEventLogTimestamp() {
        try {
            return eventsDao.getLastEventTimestamp();
        } catch (SQLException e) {
            throw new StorageException("Failed to get last event log timestamp", e);
        }
    }

    @Override
    public long getFirstEventLogId(String repoKey) {
        try {
            return eventsDao.getFirstEventId(repoKey);
        } catch (SQLException e) {
            throw new StorageException("Failed to get first event id", e);
        }
    }

    @Override
    public long getLastEventLogId() {
        try {
            return eventsDao.getLastEventId();
        } catch (SQLException e) {
            throw new StorageException("Failed to get last event log id", e);
        }
    }

    @Override
    public long getTempEventsCount() {
        try {
            return eventsDao.getTempEventsCount();
        } catch (SQLException e) {
            throw new StorageException("Failed to get event count from node events tmp", e);
        }
    }

    private CompletableFuture<SQLException> futureBounceSqlExceptionToStorageException() {
        CompletableFuture<SQLException> onException = new CompletableFuture<>();
        onException.thenAccept(e -> {
            throw new StorageException("Failed to retrieve event", e);
        });
        return onException;
    }

    private EventInfo toEventInfo(EventRecord er) {
        return new EventInfo(er.getEventId(), er.getTimestamp(), er.getType(), er.getPath(),
                EventRecordHints.fromJson(er.getHints()));
    }

    private EventRecord toEventRecord(EventInfo e, long timestamp) {
        //passing the eventId here is not important, it is not used in Dao service
        return new EventRecord(-1, timestamp, e.getType(), e.getPath(),
                EventRecordHints.toJson(e.getHints()));
    }

    @Override
    public List<EventInfo> getAllEvents() {
        try {
            List<EventRecord> records = eventsDao.loadAll();
            return records.stream().map(this::toEventInfo).collect(Collectors.toList());
        } catch (SQLException e) {
            throw new StorageException("Failed to persist event", e);
        }
    }

    @Override
    public void deleteAll() {
        try {
            eventsDao.deleteAll();
        } catch (SQLException e) {
            throw new StorageException("Failed deleting all events", e);
        }
    }

    @Override
    public void deleteRange(long startInclusive, long endExclusive) {
        try {
            eventsDao.deleteRange(startInclusive, endExclusive);
        } catch (SQLException e) {
            throw new StorageException("Failed deleting all events older than " + startInclusive, e);
        }
    }

    @Override
    public List<EventInfo> getRemainingEventsInQueue(String remoteUrl, String type, String repo, long eventId) {
        try {
            String cursorLike = repo + "-federated_replication" + remoteUrl + "%";
            List<EventRecord> records;
            if (ConstantValues.eventTableRepoKeyMigrationEnable.getBoolean()
                    && checkIfMonitorMigrationFinishedTotally()) {
                log.debug("Event table repo key migration finished get events after with repo column");
                records = eventsDao.eventsLeftForCursorToReach(cursorLike, type, repo, eventId, true);
            } else {
                log.debug("Event table repo key migration finished get events after without repo column");
                records = eventsDao.eventsLeftForCursorToReach(cursorLike, type, repo, eventId, false);
            }
            return records.stream().map(this::toEventInfo).toList();
        } catch (SQLException | ExecutionException e) {
            throw new StorageException("Failed to retrieve events", e);
        }
    }

    public boolean checkIfMonitorMigrationReadyForInsertions() {
        if (monitorMigrationFinished == null) {
            MigrationStatus migrationById = null;
            // check DB in migration table
            migrationById =
                    this.migrationStatusStorageService.findMigrationById(MONITOR_MIGRATION_NAME);
            if (migrationById != null && migrationById.isFinished()) {
                monitorMigrationFinished = migrationById.isFinished();
            } else {
                monitorMigrationFinished = false;
            }
        }
        return monitorMigrationFinished;
    }

    /**
     * Checks if the monitor migration is complete, updating the status flag and logging the attempt.
     * Returns true if the migration is complete, otherwise, it returns false.
     */
    public boolean checkIfMonitorMigrationFinishedTotally() throws ExecutionException {
        if (isEventTableRepoKeyMigrationFinished) {
            // Migration status has been checked and set to true, no need to check again
            return true;
        }

        // Try to get the value from the cache
        Boolean migrationFinished = migrationCache.get(getMonitorMigrationName());

        isEventTableRepoKeyMigrationFinished = migrationFinished;

        log.debug("Load Events: Attempting to use the repo column. Migration finished: {}",
                isEventTableRepoKeyMigrationFinished);
        return migrationFinished;
    }

    /**
     * Checks the migration status for the monitor in the database.
     * Returns true if the migration is finished; otherwise, it returns false.
     */
    private boolean checkMigrationStatus() {
        MigrationStatus eventTableRepoKeyMigration = this.migrationStatusStorageService
                .findMigrationById(MONITOR_MIGRATION_NAME);
        if (eventTableRepoKeyMigration == null) {
            log.debug("Load Events is unavailable - event repo migration not found in the DB");
            return false;
        }
        return eventTableRepoKeyMigration.isFinished();
    }

    /**
     * Shift tmp events from node_events_tmp to node_events
     * Delete node_events_tmp
     *
     * Note, this is a transactional method !
     *
     * @return repos key
     */
    @Override
    @Nonnull
    public Set<String> shiftEvents() {
        try {
            if (!markAsShifting()) {
                log.error("another thread preventing shift event");
                return Collections.emptySet();
            }
            if (ConstantValues.eventTableRepoKeyMigrationEnable.getBoolean() &&
                    checkIfMonitorMigrationReadyForInsertions()) {
                log.debug("event table repo key migration shift event with repo column");
                return eventsDao.shiftAndDeleteTmpEvents(true);
            } else {
                log.debug("event table repo key migration shift event without repo column");
                return eventsDao.shiftAndDeleteTmpEvents(false);
            }

        } catch (Exception e) {
            throw new StorageException("Failed to shift events", e);
        } finally {
            unmarkAsShifting();
        }
    }

    @Override
    public int countEventsRange(long startFrom, long end, String repoKey, String cursorType) {
        try {
            if (StringUtils.isBlank(repoKey)) {
                return eventsDao.countRangeEvents(startFrom, end);
            }
            return eventsDao.countRangeEventsByRepoPath(startFrom, end, repoKey);
        } catch (SQLException sqe) {
            log.debug("Failed to get event range from {} to {} on repo {}. Reason: {}",
                    startFrom,
                    end,
                    repoKey,
                    sqe.getMessage());
            throw new StorageException("Failed to get event range on repo " + repoKey, sqe);
        }
    }

    @Override
    public long getFirstEventIdAfterProvidedTimestamp(long timestamp, String repoKey, String cursorType) {
        try {
            if (ConstantValues.eventTableRepoKeyMigrationEnable.getBoolean() &&
                    checkIfMonitorMigrationReadyForInsertions()) {
                log.debug("event id by timestamp with repo column");
                return eventsDao.getEventIdByTimestamp(timestamp, repoKey, true);
            } else {
                log.debug("event id by timestamp without repo column");
                return eventsDao.getEventIdByTimestamp(timestamp, repoKey, false);
            }
        } catch (SQLException e) {
            String errMsg = "Failed to get event id by time on repo %s. Reason: %s".formatted(repoKey, e.getMessage());
            throw new StorageException(errMsg.formatted(repoKey), e);
        }
    }

    private void logEvents(@NonNull List<EventRecord> events) {
        if (eventLog.isInfoEnabled()) {
            events.forEach(e -> eventLog.info("{}|{}|{}", e.getTimestamp(), e.getType(), e.getPath()));
        }
        if (log.isDebugEnabled()) {
            events.forEach(e -> log.debug("{}|{}|{}", e.getTimestamp(), e.getType(), e.getPath()));
        }
    }

    public void updateMonitorMigrationFinished() {
        log.debug("event table repo key migration update monitor migration finished");
        monitorMigrationFinished = true;
    }

    public boolean markAsShifting() throws InterruptedException {
        boolean resultShiftEventLock =
                shiftEventLock.tryLock(ConstantValues.eventTableShiftEventLockTimeoutMinutes.getLong(),
                        TimeUnit.MINUTES);
        log.debug("mark as shifting event lock status:" + resultShiftEventLock);
        return resultShiftEventLock;
    }

    public void waitForShiftToEnd() throws StorageException, InterruptedException {
        try {
            boolean resultShiftLock =
                    shiftEventLock.tryLock(ConstantValues.eventTableMarkNotInShiftEventTimeoutMinutes.getLong(),
                            TimeUnit.MINUTES);
            log.debug("wait for shift to end event lock status:" + resultShiftLock);
            if (resultShiftLock) {
                unmarkAsShifting();
            } else {
                throw new StorageException("wait for shift event has timed out ");
            }
        } finally {
            unmarkAsShifting();
        }
    }

    public void unmarkAsShifting() {
        try {
            if (shiftEventLock != null) {
                if (shiftEventLock.isHeldByCurrentThread()) {
                    shiftEventLock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error when trying to marked as not in shift event, and unlock", e);
        }
    }
    public String getMonitorMigrationName(){
        return MONITOR_MIGRATION_NAME;
    }
}