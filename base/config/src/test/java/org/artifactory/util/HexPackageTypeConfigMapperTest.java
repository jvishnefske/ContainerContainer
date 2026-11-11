package org.artifactory.util;

import org.artifactory.repo.packagetype.configuration.HexConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.artifactory.util.BasePackageTypeConfigMapper.MODEL_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.HEX_REGISTRY_PUBLIC_KEY;
import static org.artifactory.util.PackageTypeConfigFields.HEX_REGISTRY_URL;


/**
 * @author Leela Padmaja
 */
class HexPackageTypeConfigMapperTest {

    @Test
    void testStandardMapping() {
        Map<String, String> packageTypeConfig =
                Map.of(MODEL_VERSION, "1",
                        HEX_REGISTRY_URL.getKey(), "https://url-1.com",
                        HEX_REGISTRY_PUBLIC_KEY.getKey(), "dummy");

        HexConfiguration hexConfiguration = HexPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        Assertions.assertEquals(1L, hexConfiguration.getModelVersion());
        Assertions.assertEquals("https://url-1.com", hexConfiguration.getHexRepositoryUrl());
        Assertions.assertEquals("dummy", hexConfiguration.getHexPublicKey());
    }

    @Test
    void testStandardMappingNoFieldInPackageTypeConfig() {
        Map<String, String> packageTypeConfig = Map.of(MODEL_VERSION, "1");

        HexConfiguration hexConfiguration = HexPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        Assertions.assertEquals(1L, hexConfiguration.getModelVersion());
        Assertions.assertEquals("https://repo.hex.pm", hexConfiguration.getHexRepositoryUrl());
        Assertions.assertEquals(HEX_REGISTRY_PUBLIC_KEY.getDefaultValue(), hexConfiguration.getHexPublicKey());

    }

}