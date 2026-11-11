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

package org.artifactory.traffic.entry;

import org.artifactory.traffic.TrafficAction;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The main implementation of the TrafficEntry interface
 *
 * @author Noam Tenne
 * @author Yoav Landman
 */
public abstract class TrafficEntryBase implements LegacyTrafficEntry {
    protected DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyyMMddHHmmss");
    protected long time;
    protected long duration;
    private final AtomicReference<String> formattedDate = new AtomicReference<>();

    /**
     * Default constructor
     */
    TrafficEntryBase() {
        this(0);
    }

    TrafficEntryBase(long duration) {
        this.time = System.currentTimeMillis();
        this.duration = duration;
    }

    public void setDateTimeFormatter(DateTimeFormatter dateTimeFormatter) {
        this.dateTimeFormatter = dateTimeFormatter;
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    public long getDuration() {
        return this.duration;
    }

    @Override
    public abstract TrafficAction getAction();

    /**
     * Compares the entry by date
     *
     * @param that Entry to compare to
     * @return int - Date comparison result
     */
    @Override
    public int compareTo(LegacyTrafficEntry that) {
        return Long.compare(time, that.getTime());
    }

    /**
     * Formats the entry date (according to the textual entry format) and returns it
     *
     * @return String - Textual entry type formatted date
     */
    String getFormattedDate() {
        //Lazy initialize if null
        formattedDate.compareAndSet(null, dateFromEpoch(time));
        return formattedDate.get();
    }

    String dateFromEpoch(long time) {
        return dateTimeFormatter.print(time);
    }
}