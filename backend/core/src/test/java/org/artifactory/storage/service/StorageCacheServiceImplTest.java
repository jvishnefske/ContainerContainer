package org.artifactory.storage.service;

import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.storage.cache.dao.StorageCacheDao;
import org.artifactory.storage.db.storage.cache.entity.StorageDbCacheEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for StorageCacheServiceImpl
 *
 * @author Evgeny Kurtser
 */
class StorageCacheServiceImplTest {

    private AutoCloseable autoCloseable;
    @Mock
    private StorageCacheDao storageCacheDao;

    private StorageCacheServiceImpl<String> service;

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        service = new StorageCacheServiceImpl<>(storageCacheDao);
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void testGet_WhenEntryDoesNotExist_ReturnsEmptyOptional() throws StorageException {
        // Given
        String type = "PROJECT";
        String key = "non-existent";
        when(storageCacheDao.getByTypeAndKey(type, key)).thenReturn(null);

        // When
        Optional<StorageCacheService.StorageCacheEntry<String>> result =
                service.get(type, key, String.class);

        // Then
        assertFalse(result.isPresent());
        verify(storageCacheDao).getByTypeAndKey(type, key);
    }

    @Test
    void testGet_WhenDaoThrowsException_PropagatesException() throws StorageException {
        // Given
        String type = "PROJECT";
        String key = "project1";
        when(storageCacheDao.getByTypeAndKey(type, key))
                .thenThrow(new StorageException("Database error"));

        // When & Then
        assertThrows(StorageException.class, () -> service.get(type, key, String.class));
        verify(storageCacheDao).getByTypeAndKey(type, key);
    }

    @Test
    void testGetByType_WhenNoEntriesExist_ReturnsEmptyOptional() throws StorageException {
        // Given
        String type = "PROJECT";
        when(storageCacheDao.getByType(type)).thenReturn(List.of());

        // When
        Optional<List<StorageCacheService.StorageCacheEntry<String>>> result =
                service.get(type, String.class);

        // Then
        assertFalse(result.isPresent());
        verify(storageCacheDao).getByType(type);
    }

    @Test
    void testGetByType_WhenEntriesExist_ReturnsParsedEntries() throws StorageException {
        // Arrange
        String type = "PROJECT";
        long timestamp1 = System.currentTimeMillis();
        long timestamp2 = timestamp1 + 1000;
        // Values are stored as JSON strings
        StorageDbCacheEntity dto1 = StorageDbCacheEntity.builder()
                .id(1L)
                .type(type)
                .key("key1")
                .value("\"value1\"")
                .calculationTimestamp(timestamp1)
                .build();
        StorageDbCacheEntity dto2 = StorageDbCacheEntity.builder()
                .id(2L)
                .type(type)
                .key("key2")
                .value("\"value2\"")
                .calculationTimestamp(timestamp2)
                .build();
        when(storageCacheDao.getByType(type)).thenReturn(List.of(dto1, dto2));

        // Act
        Optional<List<StorageCacheService.StorageCacheEntry<String>>> result =
                service.get(type, String.class);

        // Assert
        assertTrue(result.isPresent());
        List<StorageCacheService.StorageCacheEntry<String>> entries = result.get();
        assertEquals(2, entries.size());
        assertEquals("key1", entries.getFirst().key());
        assertEquals("value1", entries.get(0).value());
        assertEquals(timestamp1, entries.get(0).calculationTimestamp());
        assertEquals("key2", entries.get(1).key());
        assertEquals("value2", entries.get(1).value());
        assertEquals(timestamp2, entries.get(1).calculationTimestamp());
        verify(storageCacheDao).getByType(type);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 5})
    void testGetByType_WhenMultipleEntriesExist_ReturnsCorrectCount(int entryCount) throws StorageException {
        // Arrange
        String type = "PROJECT";
        long baseTimestamp = System.currentTimeMillis();
        List<StorageDbCacheEntity> dtos = new ArrayList<>();
        for (int i = 0; i < entryCount; i++) {
            StorageDbCacheEntity dto = StorageDbCacheEntity.builder()
                    .id((long) i + 1)
                    .type(type)
                    .key("key" + i)
                    // Values are stored as JSON strings
                    .value("\"value" + i + "\"")
                    .calculationTimestamp(baseTimestamp + i * 1000L)
                    .build();
            dtos.add(dto);
        }
        when(storageCacheDao.getByType(type)).thenReturn(dtos);

        // Act
        Optional<List<StorageCacheService.StorageCacheEntry<String>>> result =
                service.get(type, String.class);

        // Assert
        assertTrue(result.isPresent());
        List<StorageCacheService.StorageCacheEntry<String>> entries = result.get();
        assertEquals(entryCount, entries.size());
        verify(storageCacheDao).getByType(type);
    }

    @Test
    void testGetByType_WhenDaoThrowsException_PropagatesException() throws StorageException {
        // Given
        String type = "PROJECT";
        when(storageCacheDao.getByType(type))
                .thenThrow(new StorageException("Database error"));

        // When & Then
        assertThrows(StorageException.class, () -> service.get(type, String.class));
        verify(storageCacheDao).getByType(type);
    }

    @Test
    void put_shouldSaveNewEntry_whenEntryDoesNotExist() throws StorageException {
        // Arrange
        String type = "PROJECT";
        String key = "project1";
        String value = "test-value";
        when(storageCacheDao.getByTypeAndKey(type, key)).thenReturn(null);

        // Act
        service.put(type, key, value);

        // Assert
        verify(storageCacheDao).getByTypeAndKey(type, key);
        ArgumentCaptor<StorageDbCacheEntity> entityCaptor = ArgumentCaptor.forClass(StorageDbCacheEntity.class);
        verify(storageCacheDao).save(entityCaptor.capture());
        StorageDbCacheEntity capturedEntity = entityCaptor.getValue();
        assertEquals(type, capturedEntity.type());
        assertEquals(key, capturedEntity.key());
        assertEquals("\"test-value\"", capturedEntity.value());
        assertTrue(capturedEntity.calculationTimestamp() > 0);
        verify(storageCacheDao, never()).update(any());
    }

    @Test
    void putConditionally_shouldUpdateExistingEntry_whenEntryExistsAndPredicateIsNull() throws StorageException {
        // Arrange
        String type = "REPOSITORY";
        String key = "repo-key";
        String value = "serialized-value";
        String expectedJsonValue = "\"serialized-value\"";
        StorageDbCacheEntity existing = StorageDbCacheEntity.builder()
                .type(type)
                .key(key)
                .value("\"old-value\"")
                .calculationTimestamp(System.currentTimeMillis() - 1000)
                .build();
        when(storageCacheDao.getByTypeAndKey(type, key)).thenReturn(existing);

        // Act
        service.putConditionally(type, key, value, null);

        // Assert
        verify(storageCacheDao).getByTypeAndKey(type, key);
        ArgumentCaptor<StorageDbCacheEntity> entityCaptor = ArgumentCaptor.forClass(StorageDbCacheEntity.class);
        verify(storageCacheDao).update(entityCaptor.capture());
        StorageDbCacheEntity capturedEntity = entityCaptor.getValue();
        assertEquals(type, capturedEntity.type());
        assertEquals(key, capturedEntity.key());
        assertEquals(expectedJsonValue, capturedEntity.value());
        assertTrue(capturedEntity.calculationTimestamp() > 0);
        verify(storageCacheDao, never()).save(any());
    }

    @Test
    void putConditionally_shouldSaveNewEntry_whenEntryDoesNotExist() throws StorageException {
        // Arrange
        String type = "REPOSITORY";
        String key = "repo-key";
        String value = "serialized-value";
        when(storageCacheDao.getByTypeAndKey(type, key)).thenReturn(null);

        // Act
        service.putConditionally(type, key, value, null);

        // Assert
        verify(storageCacheDao).getByTypeAndKey(type, key);
        ArgumentCaptor<StorageDbCacheEntity> entityCaptor = ArgumentCaptor.forClass(StorageDbCacheEntity.class);
        verify(storageCacheDao).save(entityCaptor.capture());
        StorageDbCacheEntity capturedEntity = entityCaptor.getValue();
        assertEquals(type, capturedEntity.type());
        assertEquals(key, capturedEntity.key());
        assertEquals("\"serialized-value\"", capturedEntity.value());
        assertTrue(capturedEntity.calculationTimestamp() > 0);
        verify(storageCacheDao, never()).update(any());
    }

    @Test
    void putConditionally_shouldUpdateExistingEntry_whenPredicateReturnsTrue() throws StorageException {
        // Arrange
        String type = "PROJECT";
        String key = "project1";
        String value = "new-value";
        StorageDbCacheEntity existing = StorageDbCacheEntity.builder()
                .type(type)
                .key(key)
                .value("\"old-value\"")
                .calculationTimestamp(System.currentTimeMillis() - 1000)
                .build();
        when(storageCacheDao.getByTypeAndKey(type, key)).thenReturn(existing);

        // Act
        service.putConditionally(type, key, value, entity -> true);

        // Assert
        verify(storageCacheDao).getByTypeAndKey(type, key);
        ArgumentCaptor<StorageDbCacheEntity> entityCaptor = ArgumentCaptor.forClass(StorageDbCacheEntity.class);
        verify(storageCacheDao).update(entityCaptor.capture());
        assertEquals("\"new-value\"", entityCaptor.getValue().value());
        verify(storageCacheDao, never()).save(any());
    }

    @Test
    void putConditionally_shouldSkipUpdate_whenPredicateReturnsFalse() throws StorageException {
        // Arrange
        String type = "PROJECT";
        String key = "project1";
        String value = "new-value";
        StorageDbCacheEntity existing = StorageDbCacheEntity.builder()
                .type(type)
                .key(key)
                .value("\"old-value\"")
                .calculationTimestamp(System.currentTimeMillis() - 1000)
                .build();
        when(storageCacheDao.getByTypeAndKey(type, key)).thenReturn(existing);

        // Act
        service.putConditionally(type, key, value, entity -> false);

        // Assert
        verify(storageCacheDao).getByTypeAndKey(type, key);
        verify(storageCacheDao, never()).update(any());
        verify(storageCacheDao, never()).save(any());
    }

    @Test
    void putConditionally_shouldPropagateException_whenGetByTypeAndKeyThrowsException() throws StorageException {
        // Arrange
        String type = "PROJECT";
        String key = "project1";
        String value = "test-value";
        when(storageCacheDao.getByTypeAndKey(type, key))
                .thenThrow(new StorageException("Database error"));

        // Act & Assert
        assertThrows(StorageException.class, () -> service.putConditionally(type, key, value, null));
        verify(storageCacheDao).getByTypeAndKey(type, key);
        verify(storageCacheDao, never()).save(any());
        verify(storageCacheDao, never()).update(any());
    }

    @Test
    void putConditionally_shouldPropagateException_whenSaveThrowsException() throws StorageException {
        // Arrange
        String type = "PROJECT";
        String key = "project1";
        String value = "test-value";
        when(storageCacheDao.getByTypeAndKey(type, key)).thenReturn(null);
        ArgumentCaptor<StorageDbCacheEntity> entityCaptor = ArgumentCaptor.forClass(StorageDbCacheEntity.class);
        doThrow(new StorageException("Database error")).when(storageCacheDao).save(entityCaptor.capture());

        // Act & Assert
        assertThrows(StorageException.class, () -> service.putConditionally(type, key, value, null));
        verify(storageCacheDao).getByTypeAndKey(type, key);
        verify(storageCacheDao).save(any());
    }

    @Test
    void putConditionally_shouldPropagateException_whenUpdateThrowsException() throws StorageException {
        // Arrange
        String type = "PROJECT";
        String key = "project1";
        String value = "test-value";
        StorageDbCacheEntity existing = StorageDbCacheEntity.builder()
                .id(1L)
                .type(type)
                .key(key)
                .value("\"old-value\"")
                .calculationTimestamp(System.currentTimeMillis() - 1000)
                .build();
        when(storageCacheDao.getByTypeAndKey(type, key)).thenReturn(existing);
        ArgumentCaptor<StorageDbCacheEntity> entityCaptor = ArgumentCaptor.forClass(StorageDbCacheEntity.class);
        doThrow(new StorageException("Database error")).when(storageCacheDao).update(entityCaptor.capture());

        // Act & Assert
        assertThrows(StorageException.class, () -> service.putConditionally(type, key, value, null));
        verify(storageCacheDao).getByTypeAndKey(type, key);
        verify(storageCacheDao).update(any());
    }

    @Test
    void testRemove_WhenEntryExists_DeletesEntry() throws StorageException {
        // Given
        String type = "PROJECT";
        String key = "project1";

        // When
        service.remove(type, key);

        // Then
        verify(storageCacheDao).delete(type, key);
    }

    @Test
    void testRemove_WhenDaoThrowsException_PropagatesException() throws StorageException {
        // Given
        String type = "PROJECT";
        String key = "project1";
        doThrow(new StorageException("Database error")).when(storageCacheDao).delete(type, key);

        // When & Then
        assertThrows(StorageException.class, () -> service.remove(type, key));
        verify(storageCacheDao).delete(type, key);
    }

    @Test
    void testSize_WhenTypeProvided_ReturnsCountForType() throws StorageException {
        // Given
        String type = "PROJECT";
        int expectedSize = 5;
        when(storageCacheDao.size(type)).thenReturn(expectedSize);

        // When
        int result = service.size(type);

        // Then
        assertEquals(expectedSize, result);
        verify(storageCacheDao).size(type);
    }

    @Test
    void testSize_WhenTypeIsNull_ReturnsTotalCount() throws StorageException {
        // Given
        int expectedSize = 10;
        when(storageCacheDao.size(null)).thenReturn(expectedSize);

        // When
        int result = service.size(null);

        // Then
        assertEquals(expectedSize, result);
        verify(storageCacheDao).size(null);
    }

    @Test
    void testSize_WhenDaoThrowsException_PropagatesException() throws StorageException {
        // Given
        String type = "PROJECT";
        when(storageCacheDao.size(type))
                .thenThrow(new StorageException("Database error"));

        // When & Then
        assertThrows(StorageException.class, () -> service.size(type));
        verify(storageCacheDao).size(type);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 10})
    void removeOlderThan_shouldReturnDeletedCount_whenEntriesOlderThanThreshold(int expectedDeletedCount)
            throws StorageException {
        // Arrange
        String type = "PROJECT";
        long thresholdMilliSeconds = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L); // 7 days ago
        when(storageCacheDao.delete(type, thresholdMilliSeconds)).thenReturn(expectedDeletedCount);

        // Act
        int result = service.removeOlderThan(type, thresholdMilliSeconds);

        // Assert
        assertEquals(expectedDeletedCount, result);
        verify(storageCacheDao).delete(type, thresholdMilliSeconds);
    }

    @Test
    void removeOlderThan_shouldPropagateException_whenDaoThrowsException() throws StorageException {
        // Arrange
        String type = "PROJECT";
        long thresholdMilliSeconds = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L); // 7 days ago
        when(storageCacheDao.delete(type, thresholdMilliSeconds))
                .thenThrow(new StorageException("Database error"));

        // Act & Assert
        assertThrows(StorageException.class, () -> service.removeOlderThan(type, thresholdMilliSeconds));
        verify(storageCacheDao).delete(type, thresholdMilliSeconds);
    }

    @Test
    void removeBulk_shouldDeleteMultipleEntries_whenKeysProvided() throws StorageException {
        // Arrange
        String type = "PROJECT";
        Set<String> keys = Set.of("project1", "project2", "project3");
        int expectedDeletedCount = 3;
        when(storageCacheDao.delete(type, keys)).thenReturn(expectedDeletedCount);

        // Act
        int result = service.remove(type, keys);

        // Assert
        assertEquals(expectedDeletedCount, result);
        verify(storageCacheDao).delete(type, keys);
    }

    @Test
    void removeBulk_shouldReturnZero_whenEmptySetProvided() throws StorageException {
        // Arrange
        String type = "PROJECT";
        Set<String> keys = Set.of();
        when(storageCacheDao.delete(type, keys)).thenReturn(0);

        // Act
        int result = service.remove(type, keys);

        // Assert
        assertEquals(0, result);
        verify(storageCacheDao).delete(type, keys);
    }

    @ParameterizedTest
    @MethodSource("provideBulkRemoveTestCases")
    void removeBulk_shouldDeleteCorrectNumber_whenVariousKeySetsProvided(Set<String> keys, int expectedDeleted)
            throws StorageException {
        // Arrange
        String type = "PROJECT";
        when(storageCacheDao.delete(type, keys)).thenReturn(expectedDeleted);

        // Act
        int result = service.remove(type, keys);

        // Assert
        assertEquals(expectedDeleted, result);
        verify(storageCacheDao).delete(type, keys);
    }

    private static Stream<Arguments> provideBulkRemoveTestCases() {
        return Stream.of(
                Arguments.of(Set.of("project1"), 1),
                Arguments.of(Set.of("project1", "project2"), 2),
                Arguments.of(Set.of("project1", "project2", "project3", "project4", "project5"), 5),
                Arguments.of(Set.of("project1", "project2", "project3"), 2), // Only 2 existed in DB
                Arguments.of(new HashSet<String>(), 0) // Empty set
        );
    }

    @Test
    void removeBulk_shouldPropagateException_whenDaoThrowsException() throws StorageException {
        // Arrange
        String type = "PROJECT";
        Set<String> keys = Set.of("project1", "project2");
        when(storageCacheDao.delete(type, keys))
                .thenThrow(new StorageException("Database error"));

        // Act & Assert
        assertThrows(StorageException.class, () -> service.remove(type, keys));
        verify(storageCacheDao).delete(type, keys);
    }

    @Test
    void removeBulk_shouldHandleLargeKeySet() throws StorageException {
        // Arrange
        String type = "PROJECT";
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            keys.add("project" + i);
        }
        int expectedDeletedCount = 100;
        when(storageCacheDao.delete(type, keys)).thenReturn(expectedDeletedCount);

        // Act
        int result = service.remove(type, keys);

        // Assert
        assertEquals(expectedDeletedCount, result);
        verify(storageCacheDao).delete(type, keys);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 50, 100})
    void removeBulk_shouldHandleVariousSizes(int keyCount) throws StorageException {
        // Arrange
        String type = "PROJECT";
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < keyCount; i++) {
            keys.add("project" + i);
        }
        when(storageCacheDao.delete(type, keys)).thenReturn(keyCount);

        // Act
        int result = service.remove(type, keys);

        // Assert
        assertEquals(keyCount, result);
        verify(storageCacheDao).delete(type, keys);
    }
}
