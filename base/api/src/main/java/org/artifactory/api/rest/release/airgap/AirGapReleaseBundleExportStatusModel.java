package org.artifactory.api.rest.release.airgap;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Eli Skoran 10/08/2020
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AirGapReleaseBundleExportStatusModel {
    private AirGapReleaseBundleStatus status;
    private String message;
    private String downloadUrl;
    private String relativeDownloadUrl;

}
