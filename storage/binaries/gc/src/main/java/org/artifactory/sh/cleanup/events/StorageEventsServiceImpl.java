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

package org.artifactory.sh.cleanup.events;

import com.google.common.collect.Sets;
import lombok.NonNull;
import org.artifactory.common.ConstantValues;
import org.artifactory.event.EventHintType;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.storage.event.StorageEventInfo;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.event.dao.StorageEventsDao;
import org.jfrog.common.ClockUtils;
import org.jfrog.common.ExceptionLogUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A business service to interact with the events table.
 *
 * @author TamirH
 */
public class StorageEventsServiceImpl implements StorageEventsService {
    private static final Logger log = LoggerFactory.getLogger(StorageEventsServiceImpl.class);
    public static final String JFROG_INTERNAL_GC_HOLDER = "jfrog-internal-gc-holder";
    /**
     * Optional file logger to print events to a file. Format of the file is timestamp|type|path
     */
    private final StorageEventsDao storageEventsDao;
    private final long eventsCleanupDelayMillis;

    public StorageEventsServiceImpl(StorageEventsDao storageEventsDao) {
        this.storageEventsDao = storageEventsDao;
        eventsCleanupDelayMillis = ConstantValues.gcEventsCleanupDelayMillis.getLong();
    }

    @Override
    public long getEventsCount() {
        try {
            return storageEventsDao.getEventsCount();
        } catch (SQLException e) {
            throw new StorageException("Failed to count events", e);
        }
    }

    /**
     * Persists a list of {@link StorageEventInfo} objects to the storage and logs (debug level) them.
     *
     * @param events the list of events to be persisted and logged.
     *               If this parameter is either <em>null</em> or empty, the method does nothing.
     * @throws StorageException if an error occurs while persisting the events
     */
    @Override
    public void appendEvents(@Nullable List<StorageEventInfo> events) {
        if (events != null && !events.isEmpty()) {
            try {
                storageEventsDao.createBulk(events);
            } catch (SQLException e) {
                throw new StorageException("Failed to persist events", e);
            }
            logEvents(events);
        }
    }

    @Override
    public List<EventInfo> getEventsAfter(long eventId, @Nullable String repoKey, @Nullable String fileSuffix,
            int limit, String cursorType) {
        try {
            final List<StorageEventInfo> gcEventInfos =
                    storageEventsDao.loadEventsByParams(eventId, limit);
            return getFilteredEventsByTimestamp(gcEventInfos).stream()
                    .map(gcHint -> {
                        Set<String> gcEventHints = Sets.newHashSet(
                                EventHintType.Sha1Checksum.getPrefix() + gcHint.sha1(),
                                EventHintType.Sha256Checksum.getPrefix() + gcHint.sha256(),
                                EventHintType.BinLength.getPrefix() + gcHint.size());
                        // Add all Event GC data (currently, we support retryCount parameter only)
                        if(gcHint.data() != null) {
                            gcEventHints.add(StorageEventInfo.Data.withGCDataHintPrefix(gcHint.data()));
                        }
                        return new EventInfo(gcHint.eventId(), gcHint.timestamp(), gcHint.type(),
                                JFROG_INTERNAL_GC_HOLDER, gcEventHints);
                    }).toList();
        } catch (SQLException e) {
            throw new StorageException("Failed to retrieve storage events", e);
        }
    }

    /**
     * Filters the list of storage event information by timestamp, retaining only the events that are older
     * than the specified events cleanup delay (in milliseconds).
     *
     * @param gcEventInfos a list of storage event information to be filtered
     * @return a list containing only the events older than the specified events cleanup delay
     */
    @NonNull
    private List<StorageEventInfo> getFilteredEventsByTimestamp(List<StorageEventInfo> gcEventInfos) {
        List<StorageEventInfo> filteredEventsByTimestamp = new ArrayList<>();
        long currentTime = ClockUtils.epochMillis();
        for (StorageEventInfo storageEvents : gcEventInfos) {
            // Calculate the difference between the current time and the event timestamp
            long timeDifference = currentTime - storageEvents.timestamp();
            // If the time difference is greater than the events cleanup delay, add the event to the filtered list
            if (timeDifference > eventsCleanupDelayMillis) {
                filteredEventsByTimestamp.add(storageEvents);
            } else {
                // Break the loop if the event is not older than the specified events cleanup delay,
                // assuming the list is sorted by timestamp
                break;
            }
        }
        return filteredEventsByTimestamp;
    }

    @Override
    public int countEventsAfter(long eventId, String repoKey, String fileSuffix) {
        try {
            return storageEventsDao.countEventsByParams(eventId);
        } catch (SQLException e) {
            throw new StorageException("Failed to retrieve storage events", e);
        }
    }

    private void logEvents(@NonNull List<StorageEventInfo> events) {
        if (log.isDebugEnabled()) {
            events.forEach(e -> log.debug("{}|{}|{}", e.eventId(), e.type(), e.sha1()));
        }
    }

    @Override
    public void deleteHintsBySha(List<String> shaToDelete) {
        try {
            storageEventsDao.deleteEventsBySha1(shaToDelete);
        } catch (SQLException e) {
            log.error("Failed to clean GC hint {}", e.getMessage(), e);
        }
    }

    @Override
    public void deleteByCursor(long cursorId) {
        try {
            storageEventsDao.deleteByCursor(cursorId);
        } catch (SQLException e) {
            ExceptionLogUtils.debugOrError(log, "Failed to clean GC hint", e);
        }
    }

    @Override
    public int deleteAllPreviousBySha1(String sha1) {
        try {
            return storageEventsDao.deleteAllPreviousBySHA1(sha1);
        } catch (SQLException e) {
            ExceptionLogUtils.debugOrWarn(log, "Failed to delete previous entries of SHA1 %s".formatted(sha1), e);
            return -1;
        }
    }

    @Override
    public List<StorageEventInfo> getLatestEventsBySHA1(List<String> sha1s) {
        try {
            return storageEventsDao.getLatestEventsBySHA1(sha1s);
        } catch (SQLException e) {
            ExceptionLogUtils.debugOrWarn(log, "Failed to retrieve storage event records of list of %d SHA1s"
                    .formatted(sha1s.size()), e);
            return List.of();
        }
    }
}
