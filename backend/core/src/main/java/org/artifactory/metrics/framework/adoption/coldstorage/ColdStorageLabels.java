package org.artifactory.metrics.framework.adoption.coldstorage;

import org.artifactory.api.metrics.FeatureAdoptionLabels;
import org.artifactory.metrics.framework.adoption.LabelsBuilder;

import java.util.Map;

/**
 * Holds "dynamic labels" that are specific to Cold Storage.
 *
 * @author Oleksandr Legenkiy
 */
public enum ColdStorageLabels implements FeatureAdoptionLabels {

    ARCHIVING_POLICY_API_ADMIN(LabelsBuilder.create().entity("archiving_policy").api().admin()),
    ARCHIVING_POLICY_LIST_PAGE_UI_ADMIN(LabelsBuilder.create().entity("archiving_policy_list_page").ui().admin()),
    ARCHIVING_POLICY_UI_ADMIN(LabelsBuilder.create().entity("archiving_policy").ui().admin()),
    RESOURCE_VIA_ARCHIVING_POLICY(LabelsBuilder.create().entity("resource").details("via_archiving_policy"));

    private final Map<String, String> labels;

    ColdStorageLabels(LabelsBuilder builder) {
        this.labels = builder.build();
    }

    @Override
    public Map<String, String> labels() {
        return labels;
    }
}
