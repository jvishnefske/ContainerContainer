package org.artifactory.api.metrics;

/**
 * Holds all feature adoption actions that are tracked as part of Feature Adoption.
 * <p/>
 * NOTE: Actions should be concise and usually one word. For example: finished, visited, created, opted_out.
 * For additional details, please use "dynamic labels". See {@link FeatureAdoptionLabels} and its implementations.
 *
 * @author Oleksandr Legenkiy
 */
public enum FeatureAdoptionAction {

    ADDED("added"),
    ARCHIVED("archived"),
    CREATED("created"),
    DOWNLOADED("downloaded"),
    MIRRORED("mirrored"),
    PUBLISHED("published"),
    REGISTERED("registered"),
    TRIGGERED("triggered"),
    UPDATED("updated"),
    VISITED("visited");

    private final String action;

    FeatureAdoptionAction(String action) {
        this.action = action;
    }

    public String action() {
        return action;
    }
}
