package org.artifactory.metrics.framework.adoption;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Oleksandr Legenkiy
 */
public class LabelsBuilder {

    private static final String ENTITY_KEY = "entity";
    private static final String SUB_FEATURE_KEY = "subfeature";
    private static final String ADMIN_KEY = "admin";
    private static final String API_KEY = "api";
    private static final String UI_KEY = "ui";
    private static final String DETAILS_KEY = "details";

    private final Map<String, String> labels = new HashMap<>();

    private LabelsBuilder() {
    }

    public static LabelsBuilder create() {
        return new LabelsBuilder();
    }

    public LabelsBuilder admin() {
        this.labels.put(ADMIN_KEY, "true");
        return this;
    }

    public LabelsBuilder api() {
        this.labels.put(API_KEY, "true");
        return this;
    }

    public LabelsBuilder ui() {
        this.labels.put(UI_KEY, "true");
        return this;
    }

    public LabelsBuilder entity(String entity) {
        this.labels.put(ENTITY_KEY, entity);
        return this;
    }

    public LabelsBuilder subFeature(String subFeature) {
        this.labels.put(SUB_FEATURE_KEY, subFeature);
        return this;
    }

    public LabelsBuilder details(String details) {
        this.labels.put(DETAILS_KEY, details);
        return this;
    }

    public LabelsBuilder with(Map<String, String> labels) {
        this.labels.putAll(labels);
        return this;
    }

    public Map<String, String> build() {
        return labels;
    }
}
