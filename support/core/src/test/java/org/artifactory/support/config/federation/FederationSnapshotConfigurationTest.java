package org.artifactory.support.config.federation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FederationSnapshotConfigurationTest {

    @Test
    void defaultConstructor_shouldCreateEnabledConfiguration() {
        assertTrue(new FederationSnapshotConfiguration().isEnabled());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void parameterizedConstructor_shouldRespectEnabledFlag(boolean enabled) {
        assertEquals(enabled, new FederationSnapshotConfiguration(enabled).isEnabled());
    }
}
