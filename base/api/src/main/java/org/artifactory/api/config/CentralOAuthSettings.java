package org.artifactory.api.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.security.oauth.OAuthProviderSettings;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CentralOAuthSettings {

    boolean enabled;
    List<OAuthProviderSettings> providers;
}
