package org.artifactory.util;

import org.artifactory.repo.packagetype.configuration.PypiConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PypiPackageTypeConfigMapperTest {
    @Test
    void withValues() {
        PypiConfiguration expected = new PypiConfiguration();
        String modelVersion = "123";
        String indexContextPath = "indexContextPathV";
        String packagesContextPath = "packagesContextPathV";
        String pyPIRegistryUrl = "pyPIRegistryUrlV";
        expected.setModelVersion(Long.parseLong(modelVersion));
        expected.setIndexContextPath(indexContextPath);
        expected.setPackagesContextPath(packagesContextPath);
        expected.setPyPIRegistryUrl(pyPIRegistryUrl);
        PypiConfiguration actual = PypiPackageTypeConfigMapper.INSTANCE.map(
                Map.of("modelVersion", modelVersion, "indexContextPath", indexContextPath, "packagesContextPath",
                        packagesContextPath, "pyPIRegistryUrl", pyPIRegistryUrl));
        assertEquals(expected,actual);
    }

    @Test
    void withoutValues() {
        PypiConfiguration expected = new PypiConfiguration();
        PypiConfiguration actual = PypiPackageTypeConfigMapper.INSTANCE.map(
                Map.of());
        assertEquals(expected,actual);
    }
}
