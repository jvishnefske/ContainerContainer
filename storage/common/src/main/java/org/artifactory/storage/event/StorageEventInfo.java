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

import com.google.common.annotations.VisibleForTesting;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import org.artifactory.event.EventType;
import org.jfrog.common.JsonUtils;
import org.springframework.util.StringUtils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Represents hint for GC.
 * Implementation notes:
 * 1. Two {@linkplain StorageEventInfo} instances are considered equal
 * if they contain the same {@linkplain #type} {@linkplain #sha1}.
 * 2. Hash code of {@linkplain StorageEventInfo} instances is based on
 * hash code of its {@linkplain #type} and {@linkplain #sha1} fields.
 *
 * @author TamirH
 */
@AllArgsConstructor
@Value
@Accessors(fluent = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class StorageEventInfo {
    long eventId;
    long timestamp;
    @EqualsAndHashCode.Include
    EventType type;
    @EqualsAndHashCode.Include
    String sha1;
    String sha256;
    long size;
    Data data;


    /**
     * used in application to create a new event
     * (eventId is sequential and should not provide by application
     */
    public StorageEventInfo(EventType type, String sha1, String sha256, long size) {
        this(-1, -1, type, sha1, sha256, size, null);
    }

    public StorageEventInfo(EventType type, String sha1, String sha256, long size, Data hints) {
        this(-1L, -1L, type, sha1, sha256, size, hints);
    }

    public int getRetryCount() {
        return data == null ? 0 : data.getRetryCount();
    }

    public StorageEventInfo copyAndIncrementRetryCount() {
        StorageEventInfo res = new StorageEventInfo(type, sha1, sha256, size, data == null ?
                Data.empty() :
                new Data(data.retryCount, data.binarySync));
        res.data.incrementRetryCount();
        return res;
    }

    @lombok.Data
    // I override the parent's fluency since JSON parser requires setXXX()/getXXX() rather than plain xxx() methods.
    @Accessors(fluent = false)
    @NoArgsConstructor
    public static class Data {
        @VisibleForTesting
        static final String GC_DATA_HINT_KEY = "GC_DATA_HINT_KEY";
        private int retryCount;
        @Setter
        private boolean binarySync;

        public Data(int retryCount) {
            this(retryCount, false);
        }

        public Data(int retryCount, boolean binarySync) {
            this.retryCount = retryCount;
            this.binarySync = binarySync;
        }

        @Nullable
        public static Data fromJson(@Nullable String hintsJson) {
            if (!StringUtils.hasText(hintsJson)) {
                return null;
            }
            return JsonUtils.getInstance().readValue(hintsJson, Data.class);
        }

        @Nullable
        public static String toJson(@Nullable Data data) {
            return data == null ?
                    null :
                    JsonUtils.getInstance().valueToString(data);
        }

        @Nonnull
        public static String withGCDataHintPrefix(@Nullable Data data) {
            return data == null ? GC_DATA_HINT_KEY : GC_DATA_HINT_KEY + toJson(data);
        }

        /**
         * Retrieves an additional data associated with garbage collection (GC) event based on the provided input data.
         * This data is in JSON format and prepended with the {@linkplain #GC_DATA_HINT_KEY} prefix.
         *
         * @param hints an immutable set of data to filter for GC events.
         *              It can be {@code null} if no data are available.
         * @return data associated with GC events. If the input data are {@code null},
         * or no string prepended with the {@linkplain #GC_DATA_HINT_KEY} prefix an empty data POJO is returned.
         */
        @Nonnull
        public static Data getEventsGcData(@Nullable Set<String> hints) {
            if (hints == null) {
                return Data.empty();
            }
            return hints.stream()
                    .filter(value -> value != null && value.startsWith(GC_DATA_HINT_KEY))
                    .map(value -> value.substring(GC_DATA_HINT_KEY.length()))
                    .filter(value -> !value.isEmpty())
                    .flatMap(value -> Stream.ofNullable(fromJson(value)))
                    .findFirst()
                    .orElse(Data.empty());
        }

        /*
        I cannot use a kind of 'public static final Data EMPTY = new Data(0);'
        since this class is mutable that might spoil content of the EMPTY instance.
        */
        public static Data empty() {
            return new Data(0);
        }

        public void incrementRetryCount() {
            retryCount++;
        }
    }
}
