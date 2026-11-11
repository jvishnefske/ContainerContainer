package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.PypiConfiguration;

import java.util.Map;


public interface PypiPackageTypeConfigMapper extends BasePackageTypeConfigMapper<PypiConfiguration> {
    PypiPackageTypeConfigMapper INSTANCE = new PypiPackageTypeConfigMapper() {
        @Override
        public PypiConfiguration map(Map<String, String> packageTypeConfig) {
            PypiConfiguration pypiConfiguration = new PypiConfiguration();
            String modelVersion = packageTypeConfig.get(MODEL_VERSION);
            if (StringUtils.isNotBlank(modelVersion)) {
                pypiConfiguration.setModelVersion(Long.parseLong(modelVersion));
            }
            pypiConfiguration.setPackagesContextPath(packageTypeConfig.get("packagesContextPath"));
            pypiConfiguration.setIndexContextPath(packageTypeConfig.get("indexContextPath"));
            String pyPIRegistryUrl = packageTypeConfig.get("pyPIRegistryUrl");
            if (StringUtils.isNotBlank(pyPIRegistryUrl)) {
                pypiConfiguration.setPyPIRegistryUrl(pyPIRegistryUrl);
            }
            String repositorySuffix = packageTypeConfig.get("repositorySuffix");
            if (StringUtils.isNotBlank(repositorySuffix)) {
                pypiConfiguration.setRepositorySuffix(repositorySuffix);
            }
            return pypiConfiguration;
        }
    };
}
