package org.artifactory.probes.tomcat;

/**
 * @author Igor Usenko
 */
public enum TomcatMonitorMode {
    OFF, // do nothing
    MONITOR, // only write server readiness status to log
    ACTIVE; // server readiness affects Artifactory readiness probe

    public static TomcatMonitorMode fromString(String mode) {
        return switch (mode.toLowerCase()) {
            case "monitor" -> MONITOR;
            case "active" -> ACTIVE;
            default -> OFF;
        };
    }
}
