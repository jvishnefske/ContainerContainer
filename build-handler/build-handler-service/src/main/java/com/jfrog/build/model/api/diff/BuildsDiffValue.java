package com.jfrog.build.model.api.diff;

/**
 * @author Andrey Tashlitsky
 */
public enum BuildsDiffValue {

    UNCHANGED("Unchanged"),
    CHECKSUM("Checksum"),
    SCOPE("Scope");

    private final String description;

    BuildsDiffValue(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return description;
    }
}
