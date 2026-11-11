package com.jfrog.build.acl.model;

import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Map;

import static java.util.Arrays.stream;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toUnmodifiableMap;

@Data
@Builder
public class BuildsSearchFilter {

    private String name;
    private String buildRepo;
    private String projectKey;
    private long before;
    private long after;
    private long limit;
    private long offset;
    private String orderBy;
    private String sortOrder;

    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    public enum OrderByColumn {

        NAME("build_name"),
        DATE("build_date"),
        NUMBER("build_number");

        private final String value;
        private static final Map<String, OrderByColumn> BY_NAME = stream(values())
                .collect(toUnmodifiableMap(Enum::name, v -> v));

        @NonNull
        public static String forValue(@Nullable String orderBy) {
            return isNull(orderBy) ? DATE.value : BY_NAME.getOrDefault(orderBy.toUpperCase(), DATE).value;
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    public enum SortOrder {

        ASC("asc"),
        DESC("desc");

        private final String value;
        private static final Map<String, SortOrder> BY_NAME = stream(values())
                .collect(toUnmodifiableMap(Enum::name, v -> v));

        public static String forValue(String direction) {
            return isNull(direction) ? DESC.value : BY_NAME.getOrDefault(direction.toUpperCase(), DESC).value;
        }
    }
}
