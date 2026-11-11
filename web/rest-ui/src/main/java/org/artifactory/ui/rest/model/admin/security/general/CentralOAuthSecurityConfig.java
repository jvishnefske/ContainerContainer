package org.artifactory.ui.rest.model.admin.security.general;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.artifactory.api.config.CentralOAuthSettings;
import org.artifactory.descriptor.security.oauth.CentralOAuthProviders;
import org.artifactory.descriptor.security.oauth.OAuthProviderSettings;
import org.artifactory.rest.common.model.BaseModel;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CentralOAuthSecurityConfig extends BaseModel {

    private boolean enabled;
    private List<String> enabledProviderNames = List.of();

    public CentralOAuthSecurityConfig(@Nonnull CentralOAuthSettings yamlConfig,
            @Nullable CentralOAuthProviders descriptorConfig) {
        if (descriptorConfig != null) {
            this.enabled = descriptorConfig.getEnabled();
            this.enabledProviderNames =
                    descriptorConfig.getEnabledProviderNames() != null ? descriptorConfig.getEnabledProviderNames() :
                            List.of();
        } else {
            this.enabled = yamlConfig.isEnabled();
            this.enabledProviderNames = yamlConfig.getProviders()
                    .stream()
                    .filter(OAuthProviderSettings::getEnabled)
                    .map(OAuthProviderSettings::getName)
                    .collect(Collectors.toList());
        }
    }

    public CentralOAuthProviders toDescriptor() {
        return new CentralOAuthProviders(enabled, enabledProviderNames);
    }
}
