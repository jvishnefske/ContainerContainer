package org.artifactory.api.rest.release.airgap;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Yevdo Abramov
 * Created on 06/09/2020
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AirGapReleaseBundleImportStatusModel {
    private AirGapReleaseBundleStatus status;
}
