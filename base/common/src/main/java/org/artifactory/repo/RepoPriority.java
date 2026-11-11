package org.artifactory.repo;

/**
 * Enum representing the priority of a repository
 * @author : Tomer Ben Kereth
 */
public enum RepoPriority {
    HIGH,
    STANDARD,
    UNKNOWN;

    public static RepoPriority getPriority(String priorityLevel) {
        if (priorityLevel == null) {
            return UNKNOWN;
        }
        return switch (priorityLevel.toLowerCase()) {
            case "high"-> HIGH;
            case "standard" -> STANDARD;
            default -> UNKNOWN;
        };
    }
}
