package org.artifactory.system.load.event;

import lombok.Getter;

@Getter
public enum ConnectorLoadState {

    HEALTHY(0,"Connector is healthy - Active threads are below 80% of the maximum"),
    LOADED(1, "Connector is loaded - Active threads are above 80% of the maximum"),
    OVERLOADED(2,
            "Connector is overloaded - Active threads have reached the maximum, Tomcat connections are below the " +
                    "maximum"),
    OUTAGE(3,
            "Connector is in outage - Active threads have reached the maximum, Tomcat connections have reached the " +
                    "maximum");

    private final String description;
    private final int weight;

    ConnectorLoadState(int weight, String description) {
        this.description = description;
        this.weight = weight;
    }
}