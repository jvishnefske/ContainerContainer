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

package org.artifactory.repo.event;

import lombok.Builder;
import lombok.Getter;
import org.artifactory.event.EventType;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a modification event on the artifacts tree.
 *
 * @author Yossi Shaul
 */
@Getter
@Builder
public class EventInfo implements NodeEvent {

    long eventId;
    long timestamp;
    EventType type;
    String path;
    Set<String> hints;

    public EventInfo(long timestamp, EventType type, String path, Set<String> hints) {
        this.eventId = -1;
        this.timestamp = timestamp;
        this.type = type;
        this.path = path;
        this.hints = hints != null ? Collections.unmodifiableSet(hints) : null;
    }

    /**
     * used in application to create a new event
     * (eventId is sequential and should not provide by application
     */
    public EventInfo(EventType type, String path, Set<String> hints) {
        this(-1, type, path, hints != null ? Collections.unmodifiableSet(hints) : null);
    }

    /**
     * eventId is sequential and should not provide by application
     * hints will be added just before inserting to DB
     */
    public EventInfo(long timestamp, EventType type, String path) {
        this(timestamp, type, path, null);
    }

    /**
     * eventId is sequential and should not provide by application
     * timestamp and hints will be added just before inserting to DB
     */
    public EventInfo(EventType type, String path) {
        this(-1, type, path);
    }

    /**
     * (eventId is sequential and should be passed from the ResultSet)
     */
    public EventInfo(long eventId, long timestamp, EventType type, String path, Set<String> hints) {
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.type = type;
        this.path = path;
        this.hints = hints != null ? Collections.unmodifiableSet(hints) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventInfo eventInfo = (EventInfo) o;
        return eventId == eventInfo.eventId && type == eventInfo.type &&
                Objects.equals(path, eventInfo.path) && Objects.equals(hints, eventInfo.hints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, type, path, hints);
    }

    @Override
    public String toString() {
        return timestamp + "|" + type + "|" + path;
    }
}
