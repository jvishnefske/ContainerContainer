package org.artifactory.debug;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.annotation.JsonGetter;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Amir Avidan
 */
public record DebugLevelConfig(Map<String, Level> classes, int timer) {

    public static final DebugLevelConfig EMPTY = new DebugLevelConfig(Collections.emptyMap(), 0);

    @JsonGetter("classes")
    public Map<String, String> getClassesAsMap() {
        return classes.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toString()));
    }
}

