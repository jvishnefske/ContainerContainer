package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.PomCleanupPolicy;
import org.artifactory.repo.packagetype.configuration.MavenConfiguration;

import java.util.Optional;

import static org.artifactory.util.PackageTypeConfigFields.POM_REPOSITORY_REFERENCES_CLEANUP_POLICY;

/**
 * @author Mor Merhav
 */
public interface MavenPackageTypeConfigMapper extends BasePackageTypeConfigMapper<MavenConfiguration> {

    String SUPPRESS_POM_CONSISTENCY_CHECKS = "suppressPomConsistencyChecks";
    String FETCH_JARS_EAGERLY = "fetchJarsEagerly";
    String FETCH_SOURCES_EAGERLY = "fetchSourcesEagerly";
    String REJECT_INVALID_JARS = "rejectInvalidJars";

    MavenPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {

        MavenConfiguration config = new MavenConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);
        config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));
        config.setSuppressPomConsistencyChecks(
                Boolean.parseBoolean(packageTypeConfig.get(SUPPRESS_POM_CONSISTENCY_CHECKS)));
        config.setFetchJarsEagerly(Boolean.parseBoolean(packageTypeConfig.get(FETCH_JARS_EAGERLY)));
        config.setFetchSourcesEagerly(Boolean.parseBoolean(packageTypeConfig.get(FETCH_SOURCES_EAGERLY)));
        config.setRejectInvalidJars(Boolean.parseBoolean(packageTypeConfig.get(REJECT_INVALID_JARS)));
        Optional.ofNullable(packageTypeConfig.get(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey()))
                .map(PomCleanupPolicy::valueOf)
                .ifPresent(config::setPomRepositoryReferencesCleanupPolicy);
        return config;
    };
}