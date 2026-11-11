package org.artifactory.descriptor.repo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author shahafg
 */
class PypiGeneralConfigTest {

    @Test
    void defaultValues_shouldReturnFalse() {
        PypiGeneralConfig config = new PypiGeneralConfig();

        assertFalse(config.isSimpleJsonFormatEnabled());
        assertFalse(config.isSimpleJsonFormatEnabledExplicit());
    }

    @Test
    void allArgsConstructor_shouldSetAllValuesCorrectly() {
        PypiGeneralConfig config = new PypiGeneralConfig(true, true);

        assertTrue(config.isSimpleJsonFormatEnabled());
        assertTrue(config.isSimpleJsonFormatEnabledExplicit());
    }

    @Test
    void settersAndGetters_shouldWorkCorrectly() {
        PypiGeneralConfig config = new PypiGeneralConfig();

        config.setSimpleJsonFormatEnabled(true);
        config.setSimpleJsonFormatEnabledExplicit(true);

        assertTrue(config.isSimpleJsonFormatEnabled());
        assertTrue(config.isSimpleJsonFormatEnabledExplicit());
    }

    @Test
    void equals_shouldReturnTrue_whenConfigsHaveSameValues() {
        PypiGeneralConfig config1 = new PypiGeneralConfig(true, true);
        PypiGeneralConfig config2 = new PypiGeneralConfig(true, true);

        assertEquals(config1, config2);
    }

    @Test
    void equals_shouldReturnFalse_whenConfigsHaveDifferentValues() {
        PypiGeneralConfig config1 = new PypiGeneralConfig(true, true);
        PypiGeneralConfig config2 = new PypiGeneralConfig(false, false);

        assertNotEquals(config1, config2);
    }

    @Test
    void hashCode_shouldBeEqual_whenConfigsAreEqual() {
        PypiGeneralConfig config1 = new PypiGeneralConfig(true, true);
        PypiGeneralConfig config2 = new PypiGeneralConfig(true, true);

        assertEquals(config1.hashCode(), config2.hashCode());
    }
}
