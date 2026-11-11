package org.artifactory.util;

import org.artifactory.repo.packagetype.ExternalDependenciesConfig;

import java.util.List;
import java.util.Map;

/**
 * @author Mor Merhav
 */
public interface ExternalDependenciesPackageTypeConfigMapper
        extends BasePackageTypeConfigMapper<ExternalDependenciesConfig> {
    String EXTERNAL_DEPENDENCIES_ENABLED = "externalDependenciesEnabled";
    String EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF = "externalDependenciesRemoteRepoRef";
    String EXTERNAL_DEPENDENCIES_PATTERNS = "externalDependenciesPatterns";

    ExternalDependenciesPackageTypeConfigMapper INSTANCE = new ExternalDependenciesPackageTypeConfigMapper() {
        @Override
        public ExternalDependenciesConfig map(Map<String, String> packageTypeConfig) {
            ExternalDependenciesConfig config = new ExternalDependenciesConfig();
            String externalDepEnabled = packageTypeConfig.get(EXTERNAL_DEPENDENCIES_ENABLED);
            List<String> externalDepPatternsList =
                    jsonStringToList(EXTERNAL_DEPENDENCIES_PATTERNS, packageTypeConfig);
            String externalDepRemoteRepoRef = packageTypeConfig.get(EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF);

            config.setEnabled(Boolean.parseBoolean(externalDepEnabled));
            config.setPatterns(externalDepPatternsList);
            config.setRemoteRepoRef(externalDepRemoteRepoRef);
            return config;
        }
    };
}

