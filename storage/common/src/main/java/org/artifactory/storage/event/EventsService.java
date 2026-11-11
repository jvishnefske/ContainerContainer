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

package org.artifactory.storage.event;

import org.artifactory.repo.event.EventInfo;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A business service interface to interact with the tree events.
 *
 * @author Yossi Shaul
 */
public interface EventsService extends EventServiceBase<EventInfo> {


    @Deprecated
    List<EventInfo> getEventsByTimestamp(long timestamp);

    /**
     * @return The timestamp in millis of the first recorded event
     */
    @Deprecated
    long getFirstEventTimestamp();

    /**
     * @return The timestamp in millis of the last recorded event
     */
    @Deprecated
    long getLastEventLogTimestamp();

    /**
     * @return The id of the first recorded event
     */
    long getFirstEventLogId(String repoKey);

    /**
     * @return The id of the last recorded event
     */
    long getLastEventLogId();

    long getTempEventsCount();

    /**
     * fetch all events created after timestamp that correspond to this repoKey
     *
     * @param timestamp - events since this time
     * @param maxEvents - maxEvents to fetch
     * @return a stream of events objects
     */
    @Deprecated
    Stream<EventInfo> getEventsSince(long timestamp, long maxEvents);

    /**
     * Check if there is at least one event after the provided timestamp for that repo key
     * @return true if exists, false otherwise
     */
    boolean hasEventsAfterTimestamp(long timestamp, String repoKey);

    /**
     * Get all the events newer from the from and older than to
     *
     * @param from Beginning of the interval non-inclusive timestamp in millis
     * @param to   End of the interval non-inclusive timestamp in millis
     * @return Events newer than the given from and older then the to ordered by timestamp
     */
    @Deprecated
    List<EventInfo> getEventsInterval(long from, long to, int maxEvents, String repoKey);

    // TODO: 19/07/2020 startInclusive,endExclusive should be replaced to eventId instead of timestamp
    void deleteRange(long startInclusive, long endExclusive);

    /**
     * Shift events from node_events_tmp to the final table node_events adding sequential event_id
     *
     * @return effected repositories
     */
    @Nonnull
    Set<String> shiftEvents();

    /**
     * count events in range by repokey
     *
     * @param startFrom the starting range inclusive
     * @param end       the end timestamp non inclusive
     * @param repoKey   the events related to the repo
     * @param cursorType - The cursor type of the queue that performed the operation. This
     *       can be empty or not used depends on the service in action.
     */

    int countEventsRange(long startFrom, long end, String repoKey, String cursorType);

    /**
     * Retrieves event id that is the first after provided timestamp
     * @param timestamp - The timestamp to search upon
     * @param repoKey - The source repo
     * @param cursorType - The cursor type of the queue that performed the operation. This
     *       can be empty or not used depends on the service in action.
     * @return the event id (if found) or -1 if no event for this repo and timestamp exists.
     */

    long getFirstEventIdAfterProvidedTimestamp(long timestamp, String repoKey, String cursorType);
}
