package org.artifactory.system.load.event;

import lombok.Getter;
import org.slf4j.event.Level;

import java.util.Arrays;

import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.INFO;
import static org.slf4j.event.Level.WARN;

// Doc: ai-knowledge-base/workspace/design/load-healer-reactive-mode.md
@Getter
public enum SystemLoadState {

    HEALTHY("System is healthy - Active threads are below 80% of the maximum", INFO),
    LOADED("System is loaded - Active threads are above 80% of the maximum", INFO),
    OVERLOADED(
            "System is overloaded - Active threads have reached the maximum, Tomcat connections are below the maximum",
            WARN),
    OUTAGE(
            "System is in outage - Active threads have reached the maximum, Tomcat connections have reached the " +
                    "maximum", ERROR);

    private final String description;
    private final Level level;

    SystemLoadState(String description, Level level) {
        this.description = description;
        this.level = level;
    }

    public static SystemLoadState fromConnectorLoadState(ConnectorLoadState connectorLoadState) {
        return Arrays.stream(values()).filter(state -> state.name().equals(connectorLoadState.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ConnectorLoadState: " + connectorLoadState));
    }
}