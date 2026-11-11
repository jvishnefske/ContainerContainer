package org.artifactory.util;

import org.artifactory.repo.packagetype.configuration.YumConfiguration;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

public class YumPackageTypeConfigMapperTest {

    @Test
    public void testEmptyMap() {
        YumConfiguration configuration = YumPackageTypeConfigMapper.INSTANCE.map(Map.of());
        assertEquals(0, configuration.getModelVersion());
        assertEquals(0, configuration.getYumRootDepth());
        assertEquals(null, configuration.getYumGroupFileNames());
        assertEquals(false, configuration.isCalculateYumMetadata());
        assertEquals(false, configuration.isEnableFileListsIndexing());
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Package type config can not be null")
    public void testNull() {
        YumPackageTypeConfigMapper.INSTANCE.map(null);
    }

    @Test
    public void testMap() {
        long modelVersion = 10;
        int yumRootDepth = 100;
        String yumGroupFileNames = "names";

        Map<String, String> packageConfig = new HashMap<>();
        packageConfig.put(YumPackageTypeConfigMapper.ENABLE_FILE_LISTS_INDEXING, Boolean.TRUE.toString());
        packageConfig.put(YumPackageTypeConfigMapper.CALCULATE_YUM_METADATA, Boolean.TRUE.toString());
        packageConfig.put(YumPackageTypeConfigMapper.YUM_GROUP_FILE_NAMES, yumGroupFileNames);
        packageConfig.put(YumPackageTypeConfigMapper.YUM_ROOT_DEPTH, Integer.toString(yumRootDepth));
        packageConfig.put(BasePackageTypeConfigMapper.MODEL_VERSION, Long.toString(modelVersion));

        YumConfiguration configuration = YumPackageTypeConfigMapper.INSTANCE.map(packageConfig);
        assertEquals(yumRootDepth, configuration.getYumRootDepth());
        assertEquals(modelVersion, configuration.getModelVersion());
        assertEquals(yumGroupFileNames, configuration.getYumGroupFileNames());
        assertTrue(configuration.isCalculateYumMetadata());
        assertTrue(configuration.isEnableFileListsIndexing());
    }

}