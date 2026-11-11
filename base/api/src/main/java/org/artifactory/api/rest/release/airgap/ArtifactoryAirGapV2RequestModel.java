package org.artifactory.api.rest.release.airgap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArtifactoryAirGapV2RequestModel {
    private String jws;
}