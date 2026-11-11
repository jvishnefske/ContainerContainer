package org.artifactory.descriptor.repo;

import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author shahafg
 */
class HuggingFaceMLConfigResolverTest extends ArtifactoryHomeBoundJUnitTest {

    @BeforeEach
    void setUp() {
        setStringSystemProperty(ConstantValues.huggingFaceMLIsXetEnabled, "false");
    }

    @AfterEach
    void tearDown() {
        setStringSystemProperty(ConstantValues.huggingFaceMLIsXetEnabled, "false");
    }

    @Test
    void resolveIsXetEnabled_shouldReturnSystemProperty_whenConfigIsNull() {
        boolean result = HuggingFaceMLConfigResolver.resolveIsXetEnabled(null);

        assertFalse(result);
    }

    @Test
    void resolveIsXetEnabled_shouldReturnSystemProperty_whenConfigValueIsFalse() {
        HuggingFaceMlGeneralConfig config = new HuggingFaceMlGeneralConfig(false, false);
        setStringSystemProperty(ConstantValues.huggingFaceMLIsXetEnabled, "true");

        boolean result = HuggingFaceMLConfigResolver.resolveIsXetEnabled(config);

        assertTrue(result);
    }

    @Test
    void resolveIsXetEnabled_shouldReturnConfigValue_whenConfigValueIsTrue() {
        HuggingFaceMlGeneralConfig config = new HuggingFaceMlGeneralConfig(true, true);

        boolean result = HuggingFaceMLConfigResolver.resolveIsXetEnabled(config);

        assertTrue(result);
    }

    @Test
    void resolveIsXetEnabled_shouldReturnExplicitFalse_overTrueSystemProperty() {
        HuggingFaceMlGeneralConfig config = new HuggingFaceMlGeneralConfig(false, true);
        setStringSystemProperty(ConstantValues.huggingFaceMLIsXetEnabled, "true");

        boolean result = HuggingFaceMLConfigResolver.resolveIsXetEnabled(config);

        assertFalse(result);
    }

    @Test
    void isExplicitlyConfigured_shouldReturnFalse_whenConfigIsNull() {
        assertFalse(HuggingFaceMLConfigResolver.isExplicitlyConfigured(null));
    }

    @Test
    void isExplicitlyConfigured_shouldReturnTrue_whenExplicit() {
        HuggingFaceMlGeneralConfig config = new HuggingFaceMlGeneralConfig(true, true);

        assertTrue(HuggingFaceMLConfigResolver.isExplicitlyConfigured(config));
    }

    @Test
    void createExplicitConfiguredInstance_shouldReturnConfigWithValueAndExplicitTrue_whenGivenTrue() {
        HuggingFaceMlGeneralConfig result = HuggingFaceMLConfigResolver.createExplicitConfiguredInstance(true);

        assertTrue(result.isXetEnabled());
        assertTrue(result.isXetEnabledExplicit());
    }

    @Test
    void createExplicitConfiguredInstance_shouldReturnConfigWithValueAndExplicitTrue_whenGivenFalse() {
        HuggingFaceMlGeneralConfig result = HuggingFaceMLConfigResolver.createExplicitConfiguredInstance(false);

        assertFalse(result.isXetEnabled());
        assertTrue(result.isXetEnabledExplicit());
    }

    @Test
    void createHuggingFaceConfigBySystemProperty_shouldReturnConfigWithSystemPropertyValueAndExplicitFalse() {
        setStringSystemProperty(ConstantValues.huggingFaceMLIsXetEnabled, "true");

        HuggingFaceMlGeneralConfig result = HuggingFaceMLConfigResolver.createHuggingFaceConfigBySystemProperty();

        assertTrue(result.isXetEnabled());
        assertFalse(result.isXetEnabledExplicit());
    }
}
