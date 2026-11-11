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
class PypiConfigResolverTest extends ArtifactoryHomeBoundJUnitTest {

    @BeforeEach
    void setUp() {
        setStringSystemProperty(ConstantValues.pypiSimpleJsonFormatEnabled, "false");
    }

    @AfterEach
    void tearDown() {
        setStringSystemProperty(ConstantValues.pypiSimpleJsonFormatEnabled, "false");
    }

    @Test
    void resolveIsSimpleJsonFormatEnabled_shouldReturnSystemProperty_whenConfigIsNull() {
        boolean result = PypiConfigResolver.resolveIsSimpleJsonFormatEnabled(null);

        assertFalse(result);
    }

    @Test
    void resolveIsSimpleJsonFormatEnabled_shouldReturnSystemProperty_whenNotExplicit() {
        PypiGeneralConfig config = new PypiGeneralConfig();
        config.setSimpleJsonFormatEnabled(true);
        config.setSimpleJsonFormatEnabledExplicit(false);
        setStringSystemProperty(ConstantValues.pypiSimpleJsonFormatEnabled, "false");

        boolean result = PypiConfigResolver.resolveIsSimpleJsonFormatEnabled(config);

        assertFalse(result);
    }

    @Test
    void resolveIsSimpleJsonFormatEnabled_shouldReturnConfigValue_whenExplicit() {
        PypiGeneralConfig config = new PypiGeneralConfig();
        config.setSimpleJsonFormatEnabled(true);
        config.setSimpleJsonFormatEnabledExplicit(true);
        setStringSystemProperty(ConstantValues.pypiSimpleJsonFormatEnabled, "false");

        boolean result = PypiConfigResolver.resolveIsSimpleJsonFormatEnabled(config);

        assertTrue(result);
    }

    @Test
    void createExplicitConfiguredInstance_shouldReturnConfigWithValueAndExplicitTrue() {
        PypiGeneralConfig result = PypiConfigResolver.createExplicitConfiguredInstance(true);

        assertTrue(result.isSimpleJsonFormatEnabled());
        assertTrue(result.isSimpleJsonFormatEnabledExplicit());
    }

    @Test
    void createPypiConfigBySystemProperty_shouldReturnConfigWithSystemPropertyValuesAndExplicitFalse() {
        setStringSystemProperty(ConstantValues.pypiSimpleJsonFormatEnabled, "true");

        PypiGeneralConfig result = PypiConfigResolver.createPypiConfigBySystemProperty();

        assertTrue(result.isSimpleJsonFormatEnabled());
        assertFalse(result.isSimpleJsonFormatEnabledExplicit());
    }
}
