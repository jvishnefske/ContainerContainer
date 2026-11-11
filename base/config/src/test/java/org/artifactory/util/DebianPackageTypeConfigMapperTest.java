package org.artifactory.util;

import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.DebianConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.artifactory.util.BasePackageTypeConfigMapper.MODEL_VERSION;
import static org.artifactory.util.DebianPackageTypeConfigMapper.DDEB_SUPPORTED;
import static org.artifactory.util.DebianPackageTypeConfigMapper.DEBIAN_DEFAULT_ARCHITECTURES;
import static org.artifactory.util.DebianPackageTypeConfigMapper.DEBIAN_OPTIONAL_INDEX_COMPRESSION_FORMATS;
import static org.artifactory.util.DebianPackageTypeConfigMapper.DEBIAN_TRIVIAL_LAYOUT;
import static org.artifactory.util.DebianPackageTypeConfigMapper.OPTIONAL_INDEX_COMPRESSION_FORMATS;

/**
 * @author Tamar Rosen
 */
class DebianPackageTypeConfigMapperTest {
    @Test
    void testStandardMapping() {
        Map<String, String> packageTypeConfig =
                Map.of(MODEL_VERSION, "1",
                        DEBIAN_TRIVIAL_LAYOUT, "true",
                        OPTIONAL_INDEX_COMPRESSION_FORMATS, "[\"format-1\", \"format-2\"]",
                        DEBIAN_OPTIONAL_INDEX_COMPRESSION_FORMATS, "[\"format-3\", \"format-4\"]",
                        DDEB_SUPPORTED, "true",
                        DEBIAN_DEFAULT_ARCHITECTURES, "arch-1,arch-2",
                        "UnexpectedKey", StringUtils.EMPTY,
                        "UnexpectedKey2", "UnexpectedValue");

        DebianConfiguration debianConfiguration = DebianPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertEquals(1L, debianConfiguration.getModelVersion());
        Assertions.assertTrue(debianConfiguration.isDebianTrivialLayout());
        Assertions.assertEquals(List.of("format-1", "format-2"),
                debianConfiguration.getOptionalIndexCompressionFormats());
        Assertions.assertEquals(List.of("format-3", "format-4"),
                debianConfiguration.getDebianOptionalIndexCompressionFormats());
        Assertions.assertTrue(debianConfiguration.isDdebSupported());
        Assertions.assertEquals("arch-1,arch-2", debianConfiguration.getDebianDefaultArchitectures());
    }

    @Test
    void testAllNullsMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(MODEL_VERSION, null);
        packageTypeConfig.put(DEBIAN_TRIVIAL_LAYOUT, null);
        packageTypeConfig.put(OPTIONAL_INDEX_COMPRESSION_FORMATS, null);
        packageTypeConfig.put(DEBIAN_OPTIONAL_INDEX_COMPRESSION_FORMATS, null);
        packageTypeConfig.put(DDEB_SUPPORTED, null);
        packageTypeConfig.put(DEBIAN_DEFAULT_ARCHITECTURES, null);

        DebianConfiguration debianConfiguration = DebianPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertEquals(0, debianConfiguration.getModelVersion());
        Assertions.assertFalse(debianConfiguration.isDebianTrivialLayout());
        Assertions.assertNull(debianConfiguration.getOptionalIndexCompressionFormats());
        Assertions.assertNull(debianConfiguration.getDebianOptionalIndexCompressionFormats());
        Assertions.assertFalse(debianConfiguration.isDdebSupported());
        Assertions.assertEquals("i386,amd64", debianConfiguration.getDebianDefaultArchitectures());
    }

    @Test
    void testSomeFieldsMissing() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(MODEL_VERSION, "1");
        packageTypeConfig.put(DEBIAN_OPTIONAL_INDEX_COMPRESSION_FORMATS, "[\"format-1\", \"format-2\"]");
        packageTypeConfig.put(DDEB_SUPPORTED, "true");

        DebianConfiguration debianConfiguration = DebianPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertEquals(1, debianConfiguration.getModelVersion());
        Assertions.assertFalse(debianConfiguration.isDebianTrivialLayout());
        Assertions.assertNull(debianConfiguration.getOptionalIndexCompressionFormats());
        Assertions.assertEquals(List.of("format-1", "format-2"),
                debianConfiguration.getDebianOptionalIndexCompressionFormats());
        Assertions.assertTrue(debianConfiguration.isDdebSupported());
        Assertions.assertEquals("i386,amd64", debianConfiguration.getDebianDefaultArchitectures());
    }

    @Test
    void testEmptyLists() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(OPTIONAL_INDEX_COMPRESSION_FORMATS, "[]");
        packageTypeConfig.put(DEBIAN_OPTIONAL_INDEX_COMPRESSION_FORMATS, "[]");

        DebianConfiguration debianConfiguration = DebianPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertNotNull(debianConfiguration.getOptionalIndexCompressionFormats());
        Assertions.assertNotNull(debianConfiguration.getDebianOptionalIndexCompressionFormats());
        Assertions.assertTrue(debianConfiguration.getOptionalIndexCompressionFormats().isEmpty());
        Assertions.assertTrue(debianConfiguration.getDebianOptionalIndexCompressionFormats().isEmpty());
    }
}