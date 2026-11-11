package org.artifactory.metrics.framework.adoption.packages;

import org.artifactory.api.metrics.FeatureAdoptionLabels;
import org.artifactory.metrics.framework.adoption.LabelsBuilder;

import java.util.Map;

/**
 * Holds "dynamic labels" that are specific to Packages.
 *
 * @author Asaf Zalcman
 */
public enum PackagesLabels implements FeatureAdoptionLabels {

    SUB_FEATURE_PACKAGE(LabelsBuilder.create().subFeature("package")),
    SUB_FEATURE_REMOTE_PACKAGE(LabelsBuilder.create().subFeature("remote_package")),
    SYMBOLS_FILE(LabelsBuilder.create().entity("symbols_file")),
    SYMBOLS_PACKAGE(LabelsBuilder.create().entity("symbols_package"));

    private final Map<String, String> labels;

    PackagesLabels(LabelsBuilder builder) {
        this.labels = builder.build();
    }

    @Override
    public Map<String, String> labels() {
        return labels;
    }
}
