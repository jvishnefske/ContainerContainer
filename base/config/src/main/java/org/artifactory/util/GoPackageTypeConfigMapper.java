package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.GoConfiguration;

/**
 * @author Tamar Rosen
 */
public interface GoPackageTypeConfigMapper extends BasePackageTypeConfigMapper<GoConfiguration> {

    String GITLAB_RESOLVE_SUBGROUPS = "gitLabResolveSubgroups";

    GoPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {
        if (packageTypeConfig == null) {
            throw new IllegalArgumentException("Package type config can not be null");
        }
        GoConfiguration goConfiguration = new GoConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);

        goConfiguration.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));
        goConfiguration.setExternalDependencies(
                ExternalDependenciesPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig));

        goConfiguration.setVcs(VcsPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig));
        goConfiguration.setGitLabResolveSubgroups(
                Boolean.parseBoolean(packageTypeConfig.get(GITLAB_RESOLVE_SUBGROUPS)));
        return goConfiguration;
    };
}