package org.artifactory.ui.rest.model.admin.configuration.repository.remote;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.artifactory.rest.common.model.BaseModel;

/**
 * Request model for metadata cache period exclusion pre-validation (RTDEV-88235).
 *
 * @author Ron Malka
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MetadataCachePeriodExclusionRequestModel extends BaseModel {

    private String packageType;
    private String url;
    private Long retrievalCachePeriodSecs;
    private boolean contentSynchronisationEnabled;
}
