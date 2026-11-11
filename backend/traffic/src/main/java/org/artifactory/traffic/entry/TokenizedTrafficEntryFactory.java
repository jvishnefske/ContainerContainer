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

import org.apache.commons.lang3.StringUtils;
import org.artifactory.traffic.TrafficAction;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * @author Yoav Landman
 */
public abstract class TokenizedTrafficEntryFactory {
    private TokenizedTrafficEntryFactory() {
        // utility class
    }

    /**
     * Initializes a new traffic entry class using a textual entry
     *
     * @param entry Textual entry to create into an object
     * @return TrafficEntry - Object created from textual entry
     */
    public static LegacyTrafficEntry newTrafficEntry(String entry)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        if (StringUtils.isEmpty(entry)) {
            throw new IllegalArgumentException("Entry is empty");
        }

        String[] entryElements = StringUtils.splitPreserveAllTokens(entry, TokenizedTrafficEntry.COLUMN_SEPARATOR);
        if (entryElements.length < 6) {
            throw new IllegalArgumentException("Entry should contain at least six elements: " + entry);
        }

        // Action specification can be in the third (legacy) or forth column
        int actionIndex = (entryElements.length == TransferEntry.COLUMNS_COUNT_SPEC) ? 3 : 2;
        String action = entryElements[actionIndex];
        TrafficAction trafficAction = TrafficAction.valueOf(action);
        Constructor<? extends LegacyTrafficEntry> constructor =
                trafficAction.getTrafficEntryType().getConstructor(String.class);
        //Will init the date and duration
        return constructor.newInstance(entry);
    }
}
