package org.artifactory.descriptor.repo;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Shashank Taliwal
 */
class RpmGeneralConfigFilterTest extends ArtifactoryHomeBoundJUnitTest {

    private RpmGeneralConfig originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = new RpmGeneralConfig();
        originalConfig.getEnableSha256().setEnabled(true);
        originalConfig.getEnableRecommendTags().setEnabled(true);
    }

    @ParameterizedTest(name = "shaConst={0}, recConst={1}")
    @CsvSource({
            // shaConst, recConst, shaVisible, shaEnabled, recVisible, recEnabled
            "NA,NA,true,true,true,true",
            "true,NA,false,true,true,true",
            "NA,false,true,true,false,false",
            "false,true,false,false,false,true"
    })
    void testFilteredConfigParameterized(String shaConst, String recConst,
                                         boolean expectedShaVisible, boolean expectedShaEnabled,
                                         boolean expectedRecVisible, boolean expectedRecEnabled) {
        // Clear existing properties
        ArtifactoryHome home = ArtifactoryHome.get();
        home.getArtifactoryProperties().removeProperty(ConstantValues.yumRepomdCalculateSha2Enabled);
        home.getArtifactoryProperties().removeProperty(ConstantValues.yumInstallRecommendsEnabled);

        // Apply constant values according to parameters
        if (!"NA".equals(shaConst)) {
            home.getArtifactoryProperties()
                    .setProperty(ConstantValues.yumRepomdCalculateSha2Enabled.getPropertyName(), shaConst);
        }
        if (!"NA".equals(recConst)) {
            home.getArtifactoryProperties()
                    .setProperty(ConstantValues.yumInstallRecommendsEnabled.getPropertyName(), recConst);
        }

        RpmGeneralConfig result = RpmGeneralConfigFilter.getFilteredRpmConfig(originalConfig);

        assertNotNull(result);
        assertEquals(expectedShaVisible, result.getEnableSha256().isVisibleOnUi());
        assertEquals(expectedShaEnabled, result.getEnableSha256().isEnabled());
        assertEquals(expectedRecVisible, result.getEnableRecommendTags().isVisibleOnUi());
        assertEquals(expectedRecEnabled, result.getEnableRecommendTags().isEnabled());
    }

    @Test
    void testGetFilteredRpmConfigObjectReferences() {
        ArtifactoryHome home = ArtifactoryHome.get();
        home.getArtifactoryProperties().removeProperty(ConstantValues.yumRepomdCalculateSha2Enabled);
        home.getArtifactoryProperties().removeProperty(ConstantValues.yumInstallRecommendsEnabled);

        RpmGeneralConfig result = RpmGeneralConfigFilter.getFilteredRpmConfig(originalConfig);

        assertNotNull(result, "Result should not be null");
        assertSame(originalConfig, result,
                "Should return same object reference when no constants are set");
    }

    @Test
    void testGetFilteredRpmConfigWithNullInput() {
        ArtifactoryHome home = ArtifactoryHome.get();
        home.getArtifactoryProperties().removeProperty(ConstantValues.yumRepomdCalculateSha2Enabled);
        home.getArtifactoryProperties().removeProperty(ConstantValues.yumInstallRecommendsEnabled);

        RpmGeneralConfig result = RpmGeneralConfigFilter.getFilteredRpmConfig(null);

        assertEquals(null, result, "Should return null when input is null");
    }

    @Test
    void testGetFilteredRpmConfigWithNullNestedConfigs() {
        ArtifactoryHome home = ArtifactoryHome.get();
        home.getArtifactoryProperties()
                .setProperty(ConstantValues.yumRepomdCalculateSha2Enabled.getPropertyName(), "true");
        home.getArtifactoryProperties()
                .setProperty(ConstantValues.yumInstallRecommendsEnabled.getPropertyName(), "false");

        RpmGeneralConfig configWithNulls = new RpmGeneralConfig();
        configWithNulls.setEnableSha256(null);
        configWithNulls.setEnableRecommendTags(null);

        RpmGeneralConfig result = RpmGeneralConfigFilter.getFilteredRpmConfig(configWithNulls);

        assertNotNull(result, "Result should not be null");
    }
}
