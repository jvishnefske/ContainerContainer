package org.artifactory.ui.rest.model.admin.configuration.repository.federation;

import lombok.Data;

@Data
public class FederatedBuildInfoDetails {
    private String buildInfoRepo;
    private boolean enableConvert;
}
