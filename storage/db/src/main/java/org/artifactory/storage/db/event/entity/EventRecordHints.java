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

import lombok.experimental.UtilityClass;
import org.jfrog.common.JsonUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import jakarta.annotation.Nullable;
import java.util.Set;

/**
 * a helper class to retrieve hints from/to json representation
 * contains convert methods for the hints field in EventRecord (node_events table)
 *
 * @author Avishay Halpren
 */
@UtilityClass
public class EventRecordHints {

    @Nullable
    public static Set<String> fromJson(@Nullable String hintsJson) {
        if (!StringUtils.hasText(hintsJson)) {
            return null;
        }
        //noinspection unchecked
        return JsonUtils.getInstance().readValue(hintsJson, Set.class);
    }

    @Nullable
    public static String toJson(@Nullable Set<String> hints) {
        return !CollectionUtils.isEmpty(hints) ? JsonUtils.getInstance().valueToString(hints) : null;
    }
}
