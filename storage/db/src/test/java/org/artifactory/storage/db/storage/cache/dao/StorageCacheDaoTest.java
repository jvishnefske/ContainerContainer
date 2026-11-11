package org.artifactory.storage.db.storage.cache.dao;

import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.storage.cache.entity.StorageDbCacheEntity;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for StorageCacheDao
 *
 * @author Evgeny Kurtser
 */
class StorageCacheDaoTest {

    private StorageCacheDao dao;
    private ArtifactoryJdbcHelper jdbcHelper;

    @BeforeEach
    void setUp() {
        jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        dao = new StorageCacheDao(jdbcHelper);
    }

    @Test
    void save_shouldInsertNewEntry_whenGivenValidEntity() throws SQLException, StorageException {
        // Arrange
        String type = "PROJECT";
        String key = "project1";
        String cacheValue = "SomeValue";
        long timestamp = System.currentTimeMillis();
        StorageDbCacheEntity entity = StorageDbCacheEntity.builder()
                .type(type)
                .key(key)
                .value(cacheValue)
                .calculationTimestamp(timestamp)
                .build();

        // Act
        dao.save(entity);

        // Assert
        // INSERT_QUERY: (type, key, value, calculationTimestamp)
        verify(jdbcHelper).executeUpdate(anyString(), eq(type), eq(key), eq(cacheValue), eq(timestamp));
    }

    @Test
    void save_shouldWrapSQLException_whenDatabaseErrorOccurs() throws SQLException {
        // Arrange
        String type = "PROJECT";
        String key = "project1";
        String cacheValue = "SomeValue";
        long timestamp = System.currentTimeMillis();
        StorageDbCacheEntity entity = StorageDbCacheEntity.builder()
                .type(type)
                .key(key)
                .value(cacheValue)
                .calculationTimestamp(timestamp)
                .build();

        when(jdbcHelper.executeUpdate(anyString(), eq(type), eq(key), eq(cacheValue), eq(timestamp)))
                .thenThrow(new SQLException("Database error"));

        // Act & Assert
        assertThrows(StorageException.class, () -> dao.save(entity));
        verify(jdbcHelper).executeUpdate(anyString(), eq(type), eq(key), eq(cacheValue), eq(timestamp));
    }

    @Test
    void update_shouldUpdateExistingEntry_whenGivenValidEntity() throws SQLException, StorageException {
        // Arrange
        String type = "PROJECT";
        String key = "project1";
        String cacheValue = "SomeValue2";
        long timestamp = System.currentTimeMillis();
        StorageDbCacheEntity entity = StorageDbCacheEntity.builder()
                .type(type)
                .key(key)
                .value(cacheValue)
                .calculationTimestamp(timestamp)
                .build();

        int expectedUpdatedCount = 1;
        when(jdbcHelper.executeUpdate(anyString(), eq(cacheValue), eq(timestamp), eq(type), eq(key)))
                .thenReturn(expectedUpdatedCount);

        // Act
        int result = dao.update(entity);

        // Assert
        assertEquals(expectedUpdatedCount, result);
        // UPDATE_QUERY: (value, calculationTimestamp, type, key)
        verify(jdbcHelper).executeUpdate(anyString(), eq(cacheValue), eq(timestamp), eq(type), eq(key));
    }

    @Test
    void update_shouldReturnZero_whenNoEntryMatches() throws SQLException, StorageException {
        // Arrange
        String type = "PROJECT";
        String key = "project1";
        String cacheValue = "SomeValue2";
        long timestamp = System.currentTimeMillis();
        StorageDbCacheEntity entity = StorageDbCacheEntity.builder()
                .type(type)
                .key(key)
                .value(cacheValue)
                .calculationTimestamp(timestamp)
                .build();

        int expectedUpdatedCount = 0;
        when(jdbcHelper.executeUpdate(anyString(), eq(cacheValue), eq(timestamp), eq(type), eq(key)))
                .thenReturn(expectedUpdatedCount);

        // Act
        int result = dao.update(entity);

        // Assert
        assertEquals(expectedUpdatedCount, result);
        verify(jdbcHelper).executeUpdate(anyString(), eq(cacheValue), eq(timestamp), eq(type), eq(key));
    }

    @Test
    void update_shouldWrapSQLException_whenDatabaseErrorOccurs() throws SQLException {
        // Arrange
        String type = "PROJECT";
        String key = "project1";
        String cacheValue = "SomeValue2";
        long timestamp = System.currentTimeMillis();
        StorageDbCacheEntity entity = StorageDbCacheEntity.builder()
                .type(type)
                .key(key)
                .value(cacheValue)
                .calculationTimestamp(timestamp)
                .build();

        when(jdbcHelper.executeUpdate(anyString(), eq(cacheValue), eq(timestamp), eq(type), eq(key)))
                .thenThrow(new SQLException("Database error"));

        // Act & Assert
        assertThrows(StorageException.class, () -> dao.update(entity));
        verify(jdbcHelper).executeUpdate(anyString(), eq(cacheValue), eq(timestamp), eq(type), eq(key));
    }

    @Test
    void testGetByTypeAndKey_WhenEntryExists_ReturnsDto() throws SQLException, StorageException {
        // Given
        String type = "PROJECT";
        String key = "project1";
        long id = 1L;
        String cacheValue = "SomeValue";
        long timestamp = System.currentTimeMillis();

        ResultSet resultSet = createResultSet(id, type, key, cacheValue, timestamp);
        when(jdbcHelper.executeSelect(anyString(), eq(type), eq(key)))
                .thenReturn(resultSet);

        // When
        StorageDbCacheEntity result = dao.getByTypeAndKey(type, key);

        // Then
        assertNotNull(result);
        assertEquals(id, result.id());
        assertEquals(type, result.type());
        assertEquals(key, result.key());
        assertEquals(cacheValue, result.value());
        assertEquals(timestamp, result.calculationTimestamp());
        verify(jdbcHelper).executeSelect(anyString(), eq(type), eq(key));
    }

    @Test
    void testGetByTypeAndKey_WhenEntryDoesNotExist_ReturnsNull() throws SQLException, StorageException {
        // Given
        String type = "PROJECT";
        String key = "non-existent";

        ResultSet resultSet = createEmptyResultSet();
        when(jdbcHelper.executeSelect(anyString(), eq(type), eq(key)))
                .thenReturn(resultSet);

        // When
        StorageDbCacheEntity result = dao.getByTypeAndKey(type, key);

        // Then
        assertNull(result);
        verify(jdbcHelper).executeSelect(anyString(), eq(type), eq(key));
    }

    @Test
    void testGetByTypeAndKey_WhenSQLExceptionThrown_WrapsInStorageException() throws SQLException {
        // Given
        String type = "PROJECT";
        String key = "project1";
        when(jdbcHelper.executeSelect(anyString(), eq(type), eq(key)))
                .thenThrow(new SQLException("Database error"));

        // When & Then
        assertThrows(StorageException.class, () -> dao.getByTypeAndKey(type, key));
        verify(jdbcHelper).executeSelect(anyString(), eq(type), eq(key));
    }

    @Test
    void testGetByType_WhenEntriesExist_ReturnsList() throws SQLException, StorageException {
        // Given
        String type = "PROJECT";
        long timestamp1 = System.currentTimeMillis();
        long timestamp2 = timestamp1 + 1000;

        ResultSet resultSet = createResultSetForMultipleEntries(type, timestamp1, timestamp2);
        when(jdbcHelper.executeSelect(anyString(), eq(type)))
                .thenReturn(resultSet);

        // When
        List<StorageDbCacheEntity> result = dao.getByType(type);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("project1", result.get(0).key());
        assertEquals("project2", result.get(1).key());
        verify(jdbcHelper).executeSelect(anyString(), eq(type));
    }

    @Test
    void testGetByType_WhenNoEntriesExist_ReturnsEmptyList() throws SQLException, StorageException {
        // Given
        String type = "PROJECT";
        ResultSet resultSet = createEmptyResultSet();
        when(jdbcHelper.executeSelect(anyString(), eq(type)))
                .thenReturn(resultSet);

        // When
        List<StorageDbCacheEntity> result = dao.getByType(type);

        // Then
        assertNotNull(result);
        assertEquals(0, result.size());
        verify(jdbcHelper).executeSelect(anyString(), eq(type));
    }

    @Test
    void testGetByType_WhenSQLExceptionThrown_WrapsInStorageException() throws SQLException {
        // Given
        String type = "PROJECT";
        when(jdbcHelper.executeSelect(anyString(), eq(type)))
                .thenThrow(new SQLException("Database error"));

        // When & Then
        assertThrows(StorageException.class, () -> dao.getByType(type));
        verify(jdbcHelper).executeSelect(anyString(), eq(type));
    }

    @Test
    void testDelete_WhenEntityKeyProvided_DeletesSpecificEntry() throws SQLException, StorageException {
        // Given
        String type = "PROJECT";
        String key = "project1";
        int deletedCount = 1;
        when(jdbcHelper.executeUpdate(anyString(), eq(type), eq(key)))
                .thenReturn(deletedCount);

        // When
        int result = dao.delete(type, key);

        // Then
        assertEquals(deletedCount, result);
        verify(jdbcHelper).executeUpdate(anyString(), eq(type), eq(key));
        verify(jdbcHelper, never()).executeUpdate(anyString(), eq(type));
    }

    @Test
    void testDelete_WhenEntityKeyIsNull_DeletesAllEntriesOfType() throws SQLException, StorageException {
        // Given
        String type = "PROJECT";
        int deletedCount = 5;
        when(jdbcHelper.executeUpdate(anyString(), eq(type)))
                .thenReturn(deletedCount);

        // When
        int result = dao.delete(type, (String) null);

        // Then
        assertEquals(deletedCount, result);
        verify(jdbcHelper).executeUpdate(anyString(), eq(type));
        verify(jdbcHelper, never()).executeUpdate(anyString(), eq(type), any());
    }

    @Test
    void testDelete_WhenSQLExceptionThrown_WrapsInStorageException() throws SQLException {
        // Given
        String type = "PROJECT";
        String key = "project1";
        when(jdbcHelper.executeUpdate(anyString(), eq(type), eq(key)))
                .thenThrow(new SQLException("Database error"));

        // When & Then
        assertThrows(StorageException.class, () -> dao.delete(type, key));
        verify(jdbcHelper).executeUpdate(anyString(), eq(type), eq(key));
    }

    @Test
    void testSize_WhenTypeProvided_ReturnsCountForType() throws SQLException, StorageException {
        // Given
        String type = "PROJECT";
        int expectedCount = 5;
        ResultSet resultSet = createCountResultSet(expectedCount);
        when(jdbcHelper.executeSelect(anyString(), eq(type)))
                .thenReturn(resultSet);

        // When
        int result = dao.size(type);

        // Then
        assertEquals(expectedCount, result);
        verify(jdbcHelper).executeSelect(anyString(), eq(type));
    }

    @Test
    void testSize_WhenTypeIsNull_ReturnsTotalCount() throws SQLException, StorageException {
        // Given
        int expectedCount = 10;
        ResultSet resultSet = createCountResultSet(expectedCount);
        when(jdbcHelper.executeSelect(anyString()))
                .thenReturn(resultSet);

        // When
        int result = dao.size(null);

        // Then
        assertEquals(expectedCount, result);
        verify(jdbcHelper).executeSelect(anyString());
    }

    @Test
    void testSize_WhenNoEntries_ReturnsZero() throws SQLException, StorageException {
        // Given
        String type = "PROJECT";
        ResultSet resultSet = createCountResultSet(0);
        when(jdbcHelper.executeSelect(anyString(), eq(type)))
                .thenReturn(resultSet);

        // When
        int result = dao.size(type);

        // Then
        assertEquals(0, result);
        verify(jdbcHelper).executeSelect(anyString(), eq(type));
    }

    @Test
    void testSize_WhenResultSetIsEmpty_ReturnsZero() throws SQLException, StorageException {
        // Given
        String type = "PROJECT";
        ResultSet resultSet = createEmptyResultSet();
        when(jdbcHelper.executeSelect(anyString(), eq(type)))
                .thenReturn(resultSet);

        // When
        int result = dao.size(type);

        // Then
        assertEquals(0, result);
        verify(jdbcHelper).executeSelect(anyString(), eq(type));
    }

    @Test
    void testSize_WhenSQLExceptionThrown_WrapsInStorageException() throws SQLException {
        // Given
        String type = "PROJECT";
        when(jdbcHelper.executeSelect(anyString(), eq(type)))
                .thenThrow(new SQLException("Database error"));

        // When & Then
        assertThrows(StorageException.class, () -> dao.size(type));
        verify(jdbcHelper).executeSelect(anyString(), eq(type));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 10})
    void delete_shouldReturnDeletedCount_whenEntriesOlderThanThreshold(int expectedDeletedCount)
            throws SQLException, StorageException {
        // Arrange
        String entityType = "PROJECT";
        long thresholdMilliSeconds = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L); // 7 days ago
        when(jdbcHelper.executeUpdate(anyString(), eq(entityType), eq(thresholdMilliSeconds)))
                .thenReturn(expectedDeletedCount);

        // Act
        int result = dao.delete(entityType, thresholdMilliSeconds);

        // Assert
        assertEquals(expectedDeletedCount, result);
        verify(jdbcHelper).executeUpdate(anyString(), eq(entityType), eq(thresholdMilliSeconds));
    }

    @Test
    void delete_shouldWrapSQLException_whenDatabaseErrorOccurs() throws SQLException {
        // Arrange
        String entityType = "PROJECT";
        long thresholdMilliSeconds = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L); // 7 days ago
        when(jdbcHelper.executeUpdate(anyString(), eq(entityType), eq(thresholdMilliSeconds)))
                .thenThrow(new SQLException("Database error"));

        // Act & Assert
        assertThrows(StorageException.class, () -> dao.delete(entityType, thresholdMilliSeconds));
        verify(jdbcHelper).executeUpdate(anyString(), eq(entityType), eq(thresholdMilliSeconds));
    }

    @Test
    void deleteBulk_shouldDeleteMultipleEntries_whenKeysProvided() throws SQLException, StorageException {
        // Arrange
        String entityType = "PROJECT";
        Set<String> keys = Set.of("project1", "project2", "project3");
        int expectedDeletedCount = 3;
        when(jdbcHelper.executeUpdate(anyString(), any(), any()))
                .thenReturn(expectedDeletedCount);

        // Act
        int result = dao.delete(entityType, keys);

        // Assert
        assertEquals(expectedDeletedCount, result);

        // Capture arguments to verify Set was passed
        ArgumentCaptor<Object> arg1Captor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> arg2Captor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcHelper).executeUpdate(anyString(), arg1Captor.capture(), arg2Captor.capture());
        assertEquals(entityType, arg1Captor.getValue());
        assertInstanceOf(Set.class, arg2Captor.getValue(), "Second argument should be a Set");
        assertEquals(keys, arg2Captor.getValue());
    }

    @Test
    void deleteBulk_shouldReturnZero_whenEmptySetProvided() throws SQLException, StorageException {
        // Arrange
        String entityType = "PROJECT";
        Set<String> keys = Set.of();

        // Act
        int result = dao.delete(entityType, keys);

        // Assert
        assertEquals(0, result);
        verify(jdbcHelper, never()).executeUpdate(anyString(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("provideBulkDeleteTestCases")
    void deleteBulk_shouldDeleteCorrectNumber_whenVariousKeySetsProvided(Set<String> keys, int expectedDeleted)
            throws SQLException, StorageException {
        // Arrange
        String entityType = "PROJECT";
        if (!keys.isEmpty()) {
            when(jdbcHelper.executeUpdate(anyString(), any(), any())).thenReturn(expectedDeleted);
        }

        // Act
        int result = dao.delete(entityType, keys);

        // Assert
        assertEquals(expectedDeleted, result);
        if (keys.isEmpty()) {
            verify(jdbcHelper, never()).executeUpdate(anyString(), any(), any());
        } else {
            verify(jdbcHelper).executeUpdate(anyString(), any(), any());
        }
    }

    private static Stream<Arguments> provideBulkDeleteTestCases() {
        return Stream.of(
                Arguments.of(Set.of("project1"), 1),
                Arguments.of(Set.of("project1", "project2"), 2),
                Arguments.of(Set.of("project1", "project2", "project3", "project4", "project5"), 5),
                Arguments.of(Set.of("project1", "project2", "project3"), 2), // Only 2 existed in DB
                Arguments.of(new HashSet<String>(), 0) // Empty set
        );
    }

    @Test
    void deleteBulk_shouldWrapSQLException_whenDatabaseErrorOccurs() throws SQLException {
        // Arrange
        String entityType = "PROJECT";
        Set<String> keys = Set.of("project1", "project2");
        when(jdbcHelper.executeUpdate(anyString(), any(), any()))
                .thenThrow(new SQLException("Database error"));

        // Act & Assert
        assertThrows(StorageException.class, () -> dao.delete(entityType, keys));
        verify(jdbcHelper).executeUpdate(anyString(), any(), any());
    }

    @Test
    void deleteBulk_shouldHandleSingleKey() throws SQLException, StorageException {
        // Arrange
        String entityType = "PROJECT";
        Set<String> keys = Set.of("project1");
        int expectedDeletedCount = 1;
        when(jdbcHelper.executeUpdate(anyString(), any(), any()))
                .thenReturn(expectedDeletedCount);

        // Act
        int result = dao.delete(entityType, keys);

        // Assert
        assertEquals(expectedDeletedCount, result);
        verify(jdbcHelper).executeUpdate(anyString(), any(), any());
    }

    @Test
    void deleteBulk_shouldHandleLargeKeySet() throws SQLException, StorageException {
        // Arrange
        String entityType = "PROJECT";
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            keys.add("project" + i);
        }
        int expectedDeletedCount = 100;
        when(jdbcHelper.executeUpdate(anyString(), any(), any()))
                .thenReturn(expectedDeletedCount);

        // Act
        int result = dao.delete(entityType, keys);

        // Assert
        assertEquals(expectedDeletedCount, result);
        verify(jdbcHelper).executeUpdate(anyString(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 50, 100})
    void deleteBulk_shouldHandleVariousSizes(int keyCount) throws SQLException, StorageException {
        // Arrange
        String entityType = "PROJECT";
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < keyCount; i++) {
            keys.add("project" + i);
        }
        when(jdbcHelper.executeUpdate(anyString(), any(), any())).thenReturn(keyCount);

        // Act
        int result = dao.delete(entityType, keys);

        // Assert
        assertEquals(keyCount, result);
        verify(jdbcHelper).executeUpdate(anyString(), any(), any());
    }

    @Test
    void deleteBulk_shouldReturnPartialCount_whenOnlySomeEntriesExist() throws SQLException, StorageException {
        // Arrange
        String entityType = "PROJECT";
        Set<String> keys = Set.of("project1", "project2", "project3");
        int expectedDeletedCount = 2; // Only 2 of 3 keys existed
        when(jdbcHelper.executeUpdate(anyString(), any(), any()))
                .thenReturn(expectedDeletedCount);

        // Act
        int result = dao.delete(entityType, keys);

        // Assert
        assertEquals(expectedDeletedCount, result);
        verify(jdbcHelper).executeUpdate(anyString(), any(), any());
    }

    // Helper methods to create mock ResultSets

    private ResultSet createResultSet(long id, String type, String key, String cacheValue, long timestamp)
            throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getLong("id")).thenReturn(id);
        when(resultSet.getString("entity_type")).thenReturn(type);
        when(resultSet.getString("entity_key")).thenReturn(key);
        when(resultSet.getString("cache_value")).thenReturn(cacheValue);
        when(resultSet.getLong("calculation_timestamp")).thenReturn(timestamp);
        return resultSet;
    }

    private ResultSet createResultSetForMultipleEntries(String type, long timestamp1, long timestamp2)
            throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next())
                .thenReturn(true)  // First entry
                .thenReturn(true)  // Second entry
                .thenReturn(false); // No more entries

        // First entry
        when(resultSet.getLong("id")).thenReturn(1L, 2L);
        when(resultSet.getString("entity_type")).thenReturn(type, type);
        when(resultSet.getString("entity_key")).thenReturn("project1", "project2");
        when(resultSet.getString("cache_value")).thenReturn("SomeValue", "SomeValue2");
        when(resultSet.getLong("calculation_timestamp")).thenReturn(timestamp1, timestamp2);
        return resultSet;
    }

    private ResultSet createEmptyResultSet() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(false);
        return resultSet;
    }

    private ResultSet createCountResultSet(int count) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getInt(1)).thenReturn(count);
        return resultSet;
    }
}

