package org.artifactory.repo;

import lombok.Data;

@Data
public class FederatedMemberConfiguration {
    String url;
    String mode;
    Boolean enabled;

    public FederatedMemberConfiguration() {
        enabled = true;
    }

    public FederatedMemberConfiguration(String url, String mode, boolean enabled) {
        this.url = url;
        this.mode = mode;
        this.enabled = enabled;
    }
}
