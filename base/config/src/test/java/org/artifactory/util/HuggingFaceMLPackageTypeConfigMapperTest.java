package org.artifactory.util;

import com.google.common.collect.Maps;
import org.artifactory.repo.packagetype.configuration.HuggingFaceMLConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.artifactory.util.BasePackageTypeConfigMapper.MODEL_VERSION;
import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;

/**
 * @author Volodymyr Shulga
 */
class HuggingFaceMLPackageTypeConfigMapperTest {
    @Test
    void testStandardMapping() {
        Map<String, String> packageTypeConfig =
                Map.of(MODEL_VERSION, "1", IS_ML_REPO_LAYOUT, "true");

        HuggingFaceMLConfiguration huggingFaceConfiguration = HuggingFaceMLPackageTypeConfigMapper.INSTANCE
                .map(packageTypeConfig);
        Assertions.assertEquals(1, huggingFaceConfiguration.getModelVersion());
        Assertions.assertTrue(huggingFaceConfiguration.isMlRepoLayout());
    }

    @Test
    void testAllNullsMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(MODEL_VERSION, null);
        packageTypeConfig.put(IS_ML_REPO_LAYOUT, null);
        HuggingFaceMLConfiguration huggingFaceConfiguration = HuggingFaceMLPackageTypeConfigMapper.INSTANCE
                .map(packageTypeConfig);

        Assertions.assertEquals(0L, huggingFaceConfiguration.getModelVersion());
        Assertions.assertFalse(huggingFaceConfiguration.isMlRepoLayout());
    }

    @Test
    void testEmptyListMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();

        HuggingFaceMLConfiguration huggingFaceConfiguration = HuggingFaceMLPackageTypeConfigMapper.INSTANCE
                .map(packageTypeConfig);

        Assertions.assertNotNull(huggingFaceConfiguration);
        Assertions.assertEquals(0L, huggingFaceConfiguration.getModelVersion());
        Assertions.assertFalse(huggingFaceConfiguration.isMlRepoLayout());
    }

}