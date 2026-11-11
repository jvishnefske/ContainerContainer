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

package org.artifactory.storage.db.event.entity;

import lombok.Value;
import org.artifactory.event.EventType;

/**
 * Represents a record in the events table.
 *
 * @author Yossi Shaul
 */
@Value
public class EventRecord implements Comparable<EventRecord> {
    /**
     * identity/sequence column in DB (auto increment)
     */
    long eventId;
    long timestamp;
    EventType type;
    /**
     * Path of the item that received the event. Paths end with '/' represents a directory
     */
    String path;
    String hints;

    /**
     * Used for tests only
     * (hints are not required)
     */
    public EventRecord(long timestamp, EventType type, String path) {
        this(-1, timestamp, type, path);
    }

    /**
     * Used for tests only
     * (hints are not required)
     */
    public EventRecord(long eventId, long timestamp, EventType type, String path) {
        this(eventId, timestamp, type, path, null);
    }

    /**
     * used by application
     */
    public EventRecord(long eventId, long timestamp, EventType type, String path, String hints) {
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.type = type;
        this.path = path;
        this.hints = hints;
    }

    /**
     * Compare two events by event id.
     */
    @Override
    public int compareTo(EventRecord o) {
        return Long.compare(this.getEventId(), o.getEventId());
    }
}
