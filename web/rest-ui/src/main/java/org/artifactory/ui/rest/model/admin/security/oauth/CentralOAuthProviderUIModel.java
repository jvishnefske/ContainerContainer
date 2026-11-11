package org.artifactory.ui.rest.model.admin.security.oauth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CentralOAuthProviderUIModel {
    private String name;
    private boolean enabled;
}
