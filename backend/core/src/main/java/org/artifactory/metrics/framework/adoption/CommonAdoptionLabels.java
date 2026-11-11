package org.artifactory.metrics.framework.adoption;

import org.artifactory.api.metrics.FeatureAdoptionLabels;

import java.util.Map;

/**
 * Holds labels that are going to be common for all features and modules and can be re-used.
 *
 * @author Oleksandr Legenkiy
 */
public enum CommonAdoptionLabels implements FeatureAdoptionLabels {

    API(LabelsBuilder.create().api()),
    EMPTY(LabelsBuilder.create());

    private final Map<String, String> labels;

    CommonAdoptionLabels(LabelsBuilder builder) {
        this.labels = builder.build();
    }

    @Override
    public Map<String, String> labels() {
        return labels;
    }
}
