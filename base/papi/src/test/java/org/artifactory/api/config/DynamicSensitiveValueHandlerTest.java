package org.artifactory.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link DynamicSensitiveValueHandler#sensitiveLeafPaths} interface default, exercised through a
 * minimal implementation that does not override it.
 */
class DynamicSensitiveValueHandlerTest {

    /** Handler that implements only the mandatory methods, so the default {@code sensitiveLeafPaths} is used. */
    private static final DynamicSensitiveValueHandler<String> HANDLER = new DynamicSensitiveValueHandler<>() {
        @Override
        public void validate(String value) {
        }

        @Override
        public void encrypt(String value) {
        }

        @Override
        public void decrypt(String value) {
        }

        @Override
        public String mask(String value) {
            return value;
        }
    };

    @Test
    void sensitiveLeafPathsDefaultsToEmpty() {
        assertTrue(HANDLER.sensitiveLeafPaths("anything", "\u00BB").isEmpty());
    }
}
