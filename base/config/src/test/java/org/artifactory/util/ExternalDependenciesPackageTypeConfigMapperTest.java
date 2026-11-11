package org.artifactory.util;

import org.artifactory.repo.packagetype.ExternalDependenciesConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.artifactory.util.ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS;

class ExternalDependenciesPackageTypeConfigMapperTest {

    @Test
    void testExternalDependenciesPackageTypeConfigMapper() {
        Map<String, String> packageTypeConfig = Map.of(EXTERNAL_DEPENDENCIES_PATTERNS, "");
        ExternalDependenciesConfig map = ExternalDependenciesPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        Assertions.assertNotNull(map);
        Assertions.assertNull(map.getPatterns());
    }

}