package org.artifactory.activity;

import org.slf4j.MDC;

/**
 * The default implementation of the {@link ActivityContext} interface, which stores properties of the current
 * runtime activity in MDC. Future versions may also update OpenTracing / OpenTelemetry context.
 */
public class DefaultActivityContext implements ActivityContext {
    @Override
    public String get(String name, String defaultValue) {
        String value = MDC.get(name);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    @Override
    public ActivityContext put(String name, String value) {
        if (value == null) {
            remove(name);
        } else {
            MDC.put(name, value);
        }
        return this;
    }

    @Override
    public ActivityContext remove(String name) {
        MDC.remove(name);
        return this;
    }
}
