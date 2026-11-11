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

import java.util.List;

/**
 * A business service interface to interact with the tree events.
 *
 * @author TamirH
 */
public interface EventServiceBase<T> {

    /**
     * @return Count of all events in the log
     */
    long getEventsCount();

    /**
     * List of ordered events to add
     *
     * @param events List of ordered events to create
     */
    void appendEvents(List<T> events);

    /**
     * @param eventId    The eventId to get events after
     * @param repoKey    The repository key of events to get
     * @param fileSuffix The suffix of the file if events to get
     * @param limit      Limit the number of events receive (0 means no limit)
     * @param cursorType The cursor type of the queue that performed the operation.
     *                   This can be empty.
     * @return Ordered list of events happened since the input eventId (not inclusive) on the repo key
     */
    List<EventInfo> getEventsAfter(long eventId, String repoKey, String fileSuffix, int limit, String cursorType);

    default List<EventInfo> getEventsAfter(long eventId, String repoKey) {
        return getEventsAfter(eventId, repoKey, null, 0, "");
    }

    /**
     * count events after the specified time exclusive
     *
     * @param eventId    The timestamp from to get events after
     * @param repoKey    The repository key of events to get
     * @param fileSuffix The suffix of the file if events to get
     * @return count of events happened since the input eventId (not inclusive) on the repo key
     */
    int countEventsAfter(long eventId, String repoKey, String fileSuffix);

    default List<EventInfo> getRemainingEventsInQueue(String remoteUrl, String type, String repo, long eventId) {
        return List.of();
    }
}
