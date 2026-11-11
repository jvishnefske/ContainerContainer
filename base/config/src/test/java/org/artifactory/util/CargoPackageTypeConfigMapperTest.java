package org.artifactory.util;

import com.google.common.collect.Maps;
import org.artifactory.repo.packagetype.configuration.CargoConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * @author Tamar Rosen
 */
class CargoPackageTypeConfigMapperTest {
    @Test
    void testStandardMapping() {
        Map<String, String> packageTypeConfig =
                Map.of(CargoPackageTypeConfigMapper.MODEL_VERSION, "1",
                        CargoPackageTypeConfigMapper.CARGO_ANONYMOUS_ACCESS, "true",
                        CargoPackageTypeConfigMapper.CARGO_INTERNAL_INDEX, "true",
                        GitPackageTypeConfigMapper.GIT_REGISTRY_URL, "git-registry-url.com");

        CargoConfiguration cargoConfiguration = CargoPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertEquals(1L, cargoConfiguration.getModelVersion());
        Assertions.assertTrue(cargoConfiguration.isCargoAnonymousAccess());
        Assertions.assertTrue(cargoConfiguration.isCargoInternalIndex());
        Assertions.assertEquals("git-registry-url.com", cargoConfiguration.getGit().getRegistryUrl());
    }

    @Test
    void testAllNullsMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(CargoPackageTypeConfigMapper.MODEL_VERSION, null);
        packageTypeConfig.put(CargoPackageTypeConfigMapper.CARGO_ANONYMOUS_ACCESS, null);
        packageTypeConfig.put(CargoPackageTypeConfigMapper.CARGO_INTERNAL_INDEX, null);
        packageTypeConfig.put(GitPackageTypeConfigMapper.GIT_REGISTRY_URL, null);

        CargoConfiguration cargoConfiguration = CargoPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertEquals(0L, cargoConfiguration.getModelVersion());
        Assertions.assertFalse(cargoConfiguration.isCargoAnonymousAccess());
        Assertions.assertTrue(cargoConfiguration.isCargoInternalIndex());
        Assertions.assertNull(cargoConfiguration.getGit());
    }
}