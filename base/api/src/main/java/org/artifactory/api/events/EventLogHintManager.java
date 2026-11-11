package org.artifactory.api.events;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.event.EventHintType;
import org.artifactory.repo.RepoPath;

import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

/**
 * <p>
 * EventLogHintManager (ThreadLocal API) allows adding hints (string values) to all events (node_events) which
 * will be created in the current thread.
 * <p><br>
 * <b>Don't leave your trash for other threads!</b><br>
 * It's important to call remove() method just after add() like in every other ThreadLocal API
 * <p><br>
 * Hints are useful when extra data is needed when handing an event
 * <br>For each new use case of this API a new type should be added by the developer to EventHintType
 * <p><br>
 * E.G: When deleting an artifact we need to save it's property value as a hint to later retrieve it when
 * handling the event
 * because the original property was removed and cannot be accessed anymore
 * <p>
 * Usage:
 * <pre>
 *
 * try {
 *     EventLogHintManager.add(EventHintType.DebianIndexing, "deletedPropertyValue")
 *     //delete artifact logic here will create events stored in the DB
 *     ....
 * } finally {
 *     EventLogHintManager.remove(EventHintType.DebianIndexing)
 * }
 * </pre>
 * Later on when need to retrieve the data, it can be accessed from the event.
 * <pre>
 *
 * EventInfo myEvent = ...;
 * String value = myEvent.getHint(EventHintType.DebianIndexing);
 * </pre>
 *
 * @author avishayh
 */
@Slf4j
public class EventLogHintManager {

    private static final ThreadLocal<EventLogHintManager> current = new ThreadLocal<>();

    private final Map<EventHintType, String> hintsByType = new HashMap<>();
    private final Map<EventHintType, Map<String, String>> hintsByTypeAndPath = new HashMap<>();

    @Nullable
    public static Set<String> getHints() {
        EventLogHintManager manager = current.get();
        if (manager == null) {
            return null;
        }
        return manager.hintsByType.entrySet()
                .stream()
                .map(entry -> entry.getKey().getPrefix() + entry.getValue())
                .collect(Collectors.toSet());
    }

    @Nullable
    public static Set<String> getHints(RepoPath path) {
        EventLogHintManager manager = current.get();
        if (isNull(manager)) {
            return null;
        }
        String key = path.getCanonicalKey();
        return manager.hintsByTypeAndPath.entrySet().stream()
                .filter(entry -> entry.getValue().containsKey(key))
                .map(entry -> entry.getKey().getPrefix() + entry.getValue().get(key))
                .collect(Collectors.toSet());
    }

    public static void put(EventHintType hintTypeType, String value) {
        String existingValue = getOrCreate().hintsByType.put(hintTypeType, value);
        if (existingValue != null) {
            log.warn("Found a value for EventHintType '{}' on this thread, make sure you didn't forget to call " +
                    "remove() method", hintTypeType);
        }
    }

    public static void put(EventHintType hintTypeType, RepoPath path, String hintValue) {
        getOrCreate().hintsByTypeAndPath.computeIfAbsent(hintTypeType, key -> new HashMap<>())
                .put(path.getCanonicalKey(), hintValue);
    }

    /**
     * Removes all hints of the given type - both the plain {@link #hintsByType} entry and every path-scoped entry in
     * {@link #hintsByTypeAndPath}. Delegated to by {@link #remove(EventHintType)}.
     */
    public static void removeAll(EventHintType hintType) {
        EventLogHintManager manager = current.get();
        if (manager == null) {
            log.debug("Trying to call remove without add first");
            return;
        }
        String removed = manager.hintsByType.remove(hintType);
        Map<String, String> removedPaths = manager.hintsByTypeAndPath.remove(hintType);
        if (removed == null && removedPaths == null) {
            log.debug("Couldn't find anything to remove for EventHintType {}", hintType);
        }
        manager.cleanupIfEmpty();
    }

    public static void remove(EventHintType hintType) {
        removeAll(hintType);
    }

    /**
     * Removes a single path-scoped hint for the given type and path. Other paths of the same type are kept.
     */
    public static void remove(EventHintType hintType, RepoPath path) {
        EventLogHintManager manager = current.get();
        if (manager == null) {
            log.debug("Trying to call remove without add first");
            return;
        }
        Map<String, String> pathHints = manager.hintsByTypeAndPath.get(hintType);
        if (pathHints != null) {
            pathHints.remove(path.getCanonicalKey());
            if (pathHints.isEmpty()) {
                manager.hintsByTypeAndPath.remove(hintType);
            }
        }
        manager.cleanupIfEmpty();
    }

    /**
     * Removes all path-scoped hints for the given canonical path key leaving global ({@link #hintsByType}) hints
     * untouched. Called by event consumers once the path hints have been copied onto the committed event rows, so that
     * long-running transactions (e.g. docker pushes with many artifacts) do not accumulate large path maps in-flight.
     */
    public static void removePathHints(String pathKey) {
        EventLogHintManager manager = current.get();
        if (manager == null) {
            return;
        }
        manager.hintsByTypeAndPath.entrySet().removeIf(entry -> {
            entry.getValue().remove(pathKey);
            return entry.getValue().isEmpty();
        });
        manager.cleanupIfEmpty();
    }

    /**
     * Removes the ThreadLocal once no hints remain, so threads (e.g. pooled async/quartz threads) don't retain an
     * empty manager.
     */
    private void cleanupIfEmpty() {
        if (hintsByType.isEmpty() && hintsByTypeAndPath.isEmpty()) {
            current.remove();
        }
    }

    private static EventLogHintManager getOrCreate() {
        EventLogHintManager manager = current.get();
        if (manager == null) {
            manager = new EventLogHintManager();
            current.set(manager);
        }
        return manager;
    }


    public static boolean contains(@NonNull EventHintType hintType) {
        return EventHintType.contains(getHints(), hintType);
    }

    public static boolean contains(@NonNull EventHintType hintType, @NonNull RepoPath path) {
        return EventHintType.contains(getHints(path), hintType);
    }

    public static void clear() {
        current.remove();
    }
}
