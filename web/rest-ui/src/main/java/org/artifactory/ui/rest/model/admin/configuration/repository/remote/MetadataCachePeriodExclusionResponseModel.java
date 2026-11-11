package org.artifactory.ui.rest.model.admin.configuration.repository.remote;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.artifactory.rest.common.model.BaseModel;

/**
 * Response model for metadata cache period exclusion pre-validation (RTDEV-88235).
 *
 * @author Ron Malka
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class MetadataCachePeriodExclusionResponseModel extends BaseModel {
    private boolean allowed;
}
