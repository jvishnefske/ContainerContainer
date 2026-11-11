package org.artifactory.addon;

import java.util.Map;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

/**
 * Defines the cloud environment type
 * @author igoru
 */
public enum CloudEnvironment {

    ALIBABA("alibaba", false),
    NOT_SPECIFIED("", true);

    private static final Map<String, CloudEnvironment> BY_NAME = stream(values())
            .collect(toMap(value -> value.name, value -> value));

    private final String name;
    private final boolean viewActionEnabled;

    CloudEnvironment(String name, boolean viewActionEnabled) {
        this.name = name;
        this.viewActionEnabled = viewActionEnabled;
    }

    public boolean isViewActionEnabled() {
        return viewActionEnabled;
    }

    public static CloudEnvironment byName(String name) {
        return BY_NAME.getOrDefault(name, NOT_SPECIFIED);
    }

    public static CloudEnvironment bySystemProperty() {
        String cloudEnv = System.getProperty("shared.system.cloudEnv", "");
        return CloudEnvironment.byName(cloudEnv);
    }

}
