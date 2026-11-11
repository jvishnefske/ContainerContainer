package org.artifactory.util;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;

@UtilityClass
public class ThreadMarker {
    private static final ThreadLocal<Map<String, ThreadMarkerData>> CONTEXT = new ThreadLocal<>();

    public static void mark(@NonNull String marker) {
        getMarkersHolder().put(marker, new ThreadMarkerData());
    }

    public static void mark(@NonNull String marker, @NonNull ThreadMarkerData data) {
        getMarkersHolder().put(marker, data);
    }

    public static void unmark(@NonNull String marker) {
        getMarkersHolder().remove(marker);
        if (CONTEXT.get().isEmpty()) {
            CONTEXT.remove();
        }
    }

    public static void unmarkAll() {
        CONTEXT.remove();
    }

    public static boolean isMarkedBy(@NonNull String marker) {
        return getMarkersHolder().containsKey(marker);
    }

    public static void updateMarkerData(@NonNull String marker, @NonNull Map<String, Object> data) {
        getMarkersHolder().computeIfAbsent(marker, key -> new ThreadMarkerData()).getData().putAll(data);
    }

    @NonNull
    public static Map<String, Object> getMarkerData(@NonNull String marker) {
        return ofNullable(getMarkersHolder().get(marker)).map(ThreadMarkerData::getData).orElseGet(Map::of);
    }

    public static <T> T executeWithMarker(@NonNull String marker, @NonNull Supplier<T> supplier) {
        mark(marker);
        try {
            return supplier.get();
        } finally {
            unmark(marker);
        }
    }

    public static void executeWithMarker(@NonNull String marker, @NonNull Runnable runnable) {
        mark(marker);
        try {
            runnable.run();
        } finally {
            unmark(marker);
        }
    }

    @NonNull
    private static Map<String, ThreadMarkerData> getMarkersHolder() {
        Map<String, ThreadMarkerData> holder = CONTEXT.get();
        if (holder == null) {
            holder = new HashMap<>();
            CONTEXT.set(holder);
        }
        return holder;
    }
}