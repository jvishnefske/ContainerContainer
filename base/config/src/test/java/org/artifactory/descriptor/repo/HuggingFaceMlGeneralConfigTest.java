package org.artifactory.descriptor.repo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author shahafg
 */
class HuggingFaceMlGeneralConfigTest {

    @Test
    void defaultValues_shouldReturnFalse() {
        HuggingFaceMlGeneralConfig config = new HuggingFaceMlGeneralConfig();

        assertFalse(config.isXetEnabled());
        assertFalse(config.isXetEnabledExplicit());
    }

    @Test
    void twoArgsConstructor_shouldSetValuesCorrectly() {
        HuggingFaceMlGeneralConfig config = new HuggingFaceMlGeneralConfig(true, true);

        assertTrue(config.isXetEnabled());
        assertTrue(config.isXetEnabledExplicit());
    }

    @Test
    void settersAndGetters_shouldWorkCorrectly() {
        HuggingFaceMlGeneralConfig config = new HuggingFaceMlGeneralConfig();

        config.setXetEnabled(true);
        config.setXetEnabledExplicit(true);

        assertTrue(config.isXetEnabled());
        assertTrue(config.isXetEnabledExplicit());
    }

    @Test
    void equals_shouldReturnTrue_whenConfigsHaveSameValues() {
        HuggingFaceMlGeneralConfig config1 = new HuggingFaceMlGeneralConfig(true, true);
        HuggingFaceMlGeneralConfig config2 = new HuggingFaceMlGeneralConfig(true, true);

        assertEquals(config1, config2);
    }

    @Test
    void equals_shouldReturnFalse_whenConfigsHaveDifferentValues() {
        HuggingFaceMlGeneralConfig config1 = new HuggingFaceMlGeneralConfig(true, true);
        HuggingFaceMlGeneralConfig config2 = new HuggingFaceMlGeneralConfig(false, false);

        assertNotEquals(config1, config2);
    }

    @Test
    void hashCode_shouldBeEqual_whenConfigsAreEqual() {
        HuggingFaceMlGeneralConfig config1 = new HuggingFaceMlGeneralConfig(true, true);
        HuggingFaceMlGeneralConfig config2 = new HuggingFaceMlGeneralConfig(true, true);

        assertEquals(config1.hashCode(), config2.hashCode());
    }
}
