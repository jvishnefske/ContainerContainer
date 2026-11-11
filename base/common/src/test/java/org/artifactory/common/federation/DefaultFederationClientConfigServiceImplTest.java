package org.artifactory.common.federation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultFederationClientConfigServiceImplTest {

    private DefaultFederationClientConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DefaultFederationClientConfigServiceImpl();
    }

    @Test
    void testIsRtfsEnabled() {
        assertFalse(service.isRtfsEnabled());
    }

    @Test
    void testIsRtfsBinariesTasksEnabled() {
        assertFalse(service.isRtfsBinariesTasksEnabled());
    }

    @Test
    void testShouldUseFederationBinaryProviderV2() {
        assertFalse(service.shouldUseFederationBinaryProviderV2());
    }

    @Test
    void testGetMigrationStatus() {
        assertNull(service.getMigrationStatus());
    }

    @Test
    void testUpdateConfigKeyBoolean() {
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            service.updateConfigKey(null, true);
        });
        assertEquals("Unsupported operation: federation client is not supported", exception.getMessage());
    }

    @Test
    void testUpdateConfigKeyString() {
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            service.updateConfigKey(null, "value");
        });
        assertEquals("Unsupported operation: federation client is not supported", exception.getMessage());
    }

    @Test
    void testUpdateConfigKeyGeneric() {
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            service.updateConfigKey(null, new Object());
        });
        assertEquals("Unsupported operation: federation client is not supported", exception.getMessage());
    }

    @Test
    void testDeleteConfigKey() {
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            service.deleteConfigKey(null);
        });
        assertEquals("Unsupported operation: federation client is not supported", exception.getMessage());
    }

    @Test
    void testSetBinaryTasksUsingRtfsFlag() {
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            service.setBinaryTasksUsingRtfsFlag(true);
        });
        assertEquals("Unsupported operation: federation client is not supported", exception.getMessage());
    }

    @Test
    void testGetBooleanConfig() {
        assertFalse(service.getBooleanConfig(null));
    }

    @Test
    void testGetConfig() {
        assertNull(service.getConfig(null, Object.class));
    }
}