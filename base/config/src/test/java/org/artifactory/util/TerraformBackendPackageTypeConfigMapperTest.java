package org.artifactory.util;

import org.artifactory.repo.packagetype.configuration.TerraformBackendConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.artifactory.util.PackageTypeConfigFields.ENCRYPT_STATES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class TerraformBackendPackageTypeConfigMapperTest {

    @Test
    void testEmptyMap() {
        TerraformBackendConfiguration terraformBackendConfiguration =
                TerraformBackendPackageTypeConfigMapper.INSTANCE.map(Map.of());
        assertTrue(terraformBackendConfiguration.isEncryptStates());
    }

    @Test
    void testNull() {
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                () -> TerraformPackageTypeConfigMapper.INSTANCE.map(null));
        assertEquals("Package type config can not be null", exception.getMessage());
    }

    @Test
    void testMap() {
        long modelVersion = 10;
        Map<String, String> packageConfig = new HashMap<>();
        packageConfig.put(ENCRYPT_STATES.getKey(), "false");
        packageConfig.put("modelVersion", Long.toString(modelVersion));
        TerraformBackendConfiguration terraformBackendConfiguration =
                TerraformBackendPackageTypeConfigMapper.INSTANCE.map(packageConfig);
        assertFalse(terraformBackendConfiguration.isEncryptStates());
        assertEquals(modelVersion, terraformBackendConfiguration.getModelVersion());
    }
}