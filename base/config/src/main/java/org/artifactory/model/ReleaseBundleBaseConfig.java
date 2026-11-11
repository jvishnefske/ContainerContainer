package org.artifactory.model;

import org.artifactory.util.RepoLayoutUtils;

/**
 * Release-bundle-specific {@link BaseConfig} that always uses the simple default repo layout.
 */
class ReleaseBundleBaseConfig extends BaseConfig {

    ReleaseBundleBaseConfig(BaseConfig baseConfig) {
        super(baseConfig);
    }

    @Override
    public String getRepoLayoutRef() {
        return RepoLayoutUtils.SIMPLE_DEFAULT_NAME;
    }
}
