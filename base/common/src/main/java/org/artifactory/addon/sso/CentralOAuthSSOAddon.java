package org.artifactory.addon.sso;

import org.artifactory.common.addon.Addon;
import org.artifactory.api.config.CentralOAuthSettings;

import jakarta.annotation.Nonnull;
import java.util.List;

public interface CentralOAuthSSOAddon extends Addon {

    @Nonnull
    default CentralOAuthSettings getConfiguration() {
        return new CentralOAuthSettings(false, List.of());
    }

}
