package org.artifactory.storage.checksum.replication;

import org.artifactory.exception.http.BadRequestException;
import org.artifactory.storage.fs.service.ConfigsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.artifactory.storage.checksum.replication.StorageChecksumReplicationService.MAX_DAYS_TO_ALLOW;
import static org.artifactory.storage.fs.service.ConfigsServiceKey.STORAGE_CHECKSUM_REPLICATION;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Avishay Halpren
 */
public class StorageChecksumReplicationServiceTest {

    @ParameterizedTest
    @MethodSource("allowedParams")
    public void testUpdateValues(Boolean allowed, Integer days) {
        ConfigsService configsService = mock(ConfigsService.class);
        StorageChecksumReplicationService service = new StorageChecksumReplicationService(configsService);

        StorageChecksumReplicationModel model = new StorageChecksumReplicationModel();
        model.setCheckBinaryExistenceAllowed(allowed);
        model.setDaysToTrust(days);
        service.updateModel(model);
        verify(configsService, times(1)).addOrUpdateConfig(eq(STORAGE_CHECKSUM_REPLICATION),
                any(StorageChecksumReplicationConfig.class), anyLong());
    }

    @ParameterizedTest
    @MethodSource("wrongParams")
    public void testUpdateWrongValues(Boolean allowed, Integer days) {
        ConfigsService configsService = mock(ConfigsService.class);
        StorageChecksumReplicationService service = new StorageChecksumReplicationService(configsService);

        StorageChecksumReplicationModel model = new StorageChecksumReplicationModel();
        model.setCheckBinaryExistenceAllowed(allowed);
        model.setDaysToTrust(days);

        assertThrows(BadRequestException.class, () -> service.updateModel(model));
    }

    @Test
    public void testFoundInConfigService() {
        Class<StorageChecksumReplicationConfig> clazz = StorageChecksumReplicationConfig.class;
        ConfigsService configsService = mock(ConfigsService.class);
        StorageChecksumReplicationConfig config = mock(clazz);
        when(configsService.getConfig(eq(STORAGE_CHECKSUM_REPLICATION), eq(clazz))).thenReturn(config);
        when(config.isAllowed()).thenReturn(true);
        StorageChecksumReplicationService service = new StorageChecksumReplicationService(configsService);
        assertNotNull(service.getConfig());
        verify(configsService, times(1)).getConfig(eq(STORAGE_CHECKSUM_REPLICATION), eq(clazz));
    }

    @Test
    public void testGetConfigWithEmptyCache() {
        Class<StorageChecksumReplicationConfig> clazz = StorageChecksumReplicationConfig.class;
        ConfigsService configsService = mock(ConfigsService.class);
        StorageChecksumReplicationService service = new StorageChecksumReplicationService(configsService);
        assertFalse(service.getConfigWithCache().isAllowed());
        //calling 2nd time for getConfigWithCache and verifying only 1 call to configsService
        assertFalse(service.getConfigWithCache().isAllowed());
        verify(configsService, times(1)).getConfig(eq(STORAGE_CHECKSUM_REPLICATION), eq(clazz));
    }

    @Test
    public void testGetConfigFoundInCache() {
        Class<StorageChecksumReplicationConfig> clazz = StorageChecksumReplicationConfig.class;
        ConfigsService configsService = mock(ConfigsService.class);
        StorageChecksumReplicationConfig config = mock(clazz);
        when(configsService.getConfig(eq(STORAGE_CHECKSUM_REPLICATION), eq(clazz))).thenReturn(config);
        when(config.isAllowed()).thenReturn(true);
        StorageChecksumReplicationService service = new StorageChecksumReplicationService(configsService);
        assertTrue(service.getConfigWithCache().isAllowed());
        //calling 2nd time for getConfigWithCache and verifying only 1 call to configsService
        assertTrue(service.getConfigWithCache().isAllowed());
        verify(configsService, times(1)).getConfig(eq(STORAGE_CHECKSUM_REPLICATION), eq(clazz));
    }

    @Test
    public void testGetModelNotFound() {
        ConfigsService configsService = mock(ConfigsService.class);
        StorageChecksumReplicationService service = new StorageChecksumReplicationService(configsService);
        assertNull(service.getModel());
    }

    @Test
    public void testGetModelFound() {
        Class<StorageChecksumReplicationConfig> clazz = StorageChecksumReplicationConfig.class;
        ConfigsService configsService = mock(ConfigsService.class);
        StorageChecksumReplicationConfig config = mock(clazz);
        when(configsService.getConfig(eq(STORAGE_CHECKSUM_REPLICATION), eq(clazz))).thenReturn(config);
        when(config.isAllowed()).thenReturn(true);
        StorageChecksumReplicationService service = new StorageChecksumReplicationService(configsService);
        assertNotNull(service.getModel());
        verify(configsService, times(1)).getConfig(eq(STORAGE_CHECKSUM_REPLICATION), eq(clazz));
    }

    @Test
    public void testNotFoundInConfigService() {
        Class<StorageChecksumReplicationConfig> clazz = StorageChecksumReplicationConfig.class;
        ConfigsService configsService = mock(ConfigsService.class);
        when(configsService.getConfig(eq(STORAGE_CHECKSUM_REPLICATION), eq(clazz))).thenReturn(null);
        StorageChecksumReplicationService service = new StorageChecksumReplicationService(configsService);
        assertNull(service.getConfig());
        verify(configsService, times(1)).getConfig(eq(STORAGE_CHECKSUM_REPLICATION), eq(clazz));
    }

    public static Stream<Arguments> allowedParams() {
        return Stream.of(
                //allowed, days
                arguments(true, 3),
                arguments(true, MAX_DAYS_TO_ALLOW),
                arguments(true, null),
                arguments(true, 0),
                arguments(false, null));
    }

    public static Stream<Arguments> wrongParams() {
        return Stream.of(
                // allowed, days
                arguments(null, 3),
                arguments(false, 3),
                arguments(true, -1),
                arguments(true, MAX_DAYS_TO_ALLOW + 1)

        );
    }
}