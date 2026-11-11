package org.artifactory.addon.huggingfaceml;

import lombok.Getter;

import static org.artifactory.addon.huggingfaceml.HuggingFacePathUtil.DATASET_LEAD_FILE_NAME;
import static org.artifactory.addon.huggingfaceml.HuggingFacePathUtil.LATEST_DATASET_LEAD_FILE_NAME;
import static org.artifactory.addon.huggingfaceml.HuggingFacePathUtil.LATEST_MODEL_LEAD_FILE_NAME;
import static org.artifactory.addon.huggingfaceml.HuggingFacePathUtil.MODEL_LEAD_FILE_NAME;

/**
 * @author shahafg
 */
@Getter
public enum EntityType {
    MODEL(LATEST_MODEL_LEAD_FILE_NAME, MODEL_LEAD_FILE_NAME),
    DATASET(LATEST_DATASET_LEAD_FILE_NAME, DATASET_LEAD_FILE_NAME),
    UNKNOWN(null, null);

    private final String latestLeadFileName;

    private final String leadFileName;

    EntityType(String latestLeadFileName, String leadFileName) {
        this.leadFileName = leadFileName;
        this.latestLeadFileName = latestLeadFileName;
    }
}