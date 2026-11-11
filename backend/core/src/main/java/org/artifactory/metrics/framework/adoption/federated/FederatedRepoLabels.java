package org.artifactory.metrics.framework.adoption.federated;

import org.artifactory.api.metrics.FeatureAdoptionLabels;
import org.artifactory.metrics.framework.adoption.LabelsBuilder;

import java.util.Map;

/**
 * Holds "dynamic labels" that are specific to Federated Repo.
 *
 * @author Oleksandr Legenkiy
 */
public enum FederatedRepoLabels implements FeatureAdoptionLabels {

    ARTIFACT_TO_MEMBER(LabelsBuilder.create().entity("artifact").details("to_member")),
    FEDERATED_REPO(LabelsBuilder.create().entity("federated_repo")),
    FEDERATED_MEMBER(LabelsBuilder.create().entity("federated_member")),
    FULL_SYNC(LabelsBuilder.create().entity("full_sync")),
    TOKEN(LabelsBuilder.create().entity("token"));


    private final Map<String, String> labels;

    FederatedRepoLabels(LabelsBuilder builder) {
        this.labels = builder.build();
    }

    @Override
    public Map<String, String> labels() {
        return labels;
    }
}
