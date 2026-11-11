/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.util.limit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.artifactory.aql.QueryBlockedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Test class for LimitedResultSet
 * Tests the row limiting functionality with both blocking and alerting modes
 *
 * @author Eli Fishman
 */
@ExtendWith(MockitoExtension.class)
class LimitedResultSetTest {

    @Mock
    private ResultSet mockResultSet;

    private LimitedResultSet limitedResultSet;
    private static final String TEST_QUERY = "SELECT * FROM test_table";

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(mockResultSet);
    }

    @Test
    void testConstructor() {
        // Test constructor with blocking mode
        limitedResultSet = new LimitedResultSet(mockResultSet, 100, true, TEST_QUERY);
        assertEquals(100, limitedResultSet.getLimit());
        assertTrue(limitedResultSet.isToBlock());
        assertEquals(0, limitedResultSet.getRowsCount());

        // Test constructor with alerting mode
        limitedResultSet = new LimitedResultSet(mockResultSet, 50, false, TEST_QUERY);
        assertEquals(50, limitedResultSet.getLimit());
        assertFalse(limitedResultSet.isToBlock());
        assertEquals(0, limitedResultSet.getRowsCount());
    }

    @Test
    void testNextWithinLimit() throws SQLException {
        // Setup: mock resultSet to return true for next() calls within limit
        when(mockResultSet.next()).thenReturn(true, true, true, false);
        
        limitedResultSet = new LimitedResultSet(mockResultSet, 5, true, TEST_QUERY);

        // Test first call
        assertTrue(limitedResultSet.next());
        assertEquals(1, limitedResultSet.getRowsCount());

        // Test second call
        assertTrue(limitedResultSet.next());
        assertEquals(2, limitedResultSet.getRowsCount());

        // Test third call
        assertTrue(limitedResultSet.next());
        assertEquals(3, limitedResultSet.getRowsCount());

        // Test when underlying resultSet returns false
        assertFalse(limitedResultSet.next());
        assertEquals(3, limitedResultSet.getRowsCount());

        // Verify that next() was called 4 times on the underlying ResultSet
        verify(mockResultSet, times(4)).next();
    }

    @Test
    void testNextExceedsLimitWithBlocking() throws SQLException {
        when(mockResultSet.next()).thenReturn(true, true, true, true, true, false);

        limitedResultSet = new LimitedResultSet(mockResultSet, 3, true, TEST_QUERY);

        assertTrue(limitedResultSet.next());
        assertEquals(1, limitedResultSet.getRowsCount());

        assertTrue(limitedResultSet.next());
        assertEquals(2, limitedResultSet.getRowsCount());

        assertTrue(limitedResultSet.next());
        assertEquals(3, limitedResultSet.getRowsCount());

        QueryBlockedException exception =
                assertThrows(QueryBlockedException.class, () -> limitedResultSet.next());
        assertEquals(4L, exception.getRowsCount());
        assertEquals(3, exception.getLimit());
        assertEquals(TEST_QUERY, exception.getQuery());
        assertEquals(4, limitedResultSet.getRowsCount());

        verify(mockResultSet, times(4)).next();
    }

    @Test
    void testNextExceedsLimitWithAlerting() throws SQLException {
        // Setup: mock resultSet to return true for more calls than the limit
        when(mockResultSet.next()).thenReturn(true, true, true, true, true, false);
        
        limitedResultSet = new LimitedResultSet(mockResultSet, 3, false, TEST_QUERY);

        // Test calls within limit
        assertTrue(limitedResultSet.next());
        assertEquals(1, limitedResultSet.getRowsCount());

        assertTrue(limitedResultSet.next());
        assertEquals(2, limitedResultSet.getRowsCount());

        assertTrue(limitedResultSet.next());
        assertEquals(3, limitedResultSet.getRowsCount());

        // Test call that exceeds limit - should not throw exception but continue
        assertTrue(limitedResultSet.next());
        assertEquals(4, limitedResultSet.getRowsCount());

        // Test another call that exceeds limit - should still not throw exception
        assertTrue(limitedResultSet.next());
        assertEquals(5, limitedResultSet.getRowsCount());

        // Test when underlying resultSet returns false
        assertFalse(limitedResultSet.next());
        assertEquals(5, limitedResultSet.getRowsCount());

        // Verify that next() was called 6 times on the underlying ResultSet
        verify(mockResultSet, times(6)).next();
    }

    @Test
    void testNextWithZeroLimit() throws SQLException {
        when(mockResultSet.next()).thenReturn(true, false);

        limitedResultSet = new LimitedResultSet(mockResultSet, 0, true, TEST_QUERY);

        QueryBlockedException exception =
                assertThrows(QueryBlockedException.class, () -> limitedResultSet.next());
        assertEquals(1L, exception.getRowsCount());
        assertEquals(0, exception.getLimit());
        assertEquals(TEST_QUERY, exception.getQuery());
        assertEquals(1, limitedResultSet.getRowsCount());

        verify(mockResultSet, times(1)).next();
    }

    @Test
    void testNextWithZeroLimitAlerting() throws SQLException {
        // Setup: mock resultSet to return true
        when(mockResultSet.next()).thenReturn(true, false);
        
        limitedResultSet = new LimitedResultSet(mockResultSet, 0, false, TEST_QUERY);

        // First call should exceed limit but not throw exception
        assertTrue(limitedResultSet.next());
        assertEquals(1, limitedResultSet.getRowsCount());

        // Second call should return false (end of result set)
        assertFalse(limitedResultSet.next());
        assertEquals(1, limitedResultSet.getRowsCount());

        // Verify that next() was called 2 times on the underlying ResultSet
        verify(mockResultSet, times(2)).next();
    }

    @Test
    void testNextWithNegativeLimit() throws SQLException {
        when(mockResultSet.next()).thenReturn(true, false);

        limitedResultSet = new LimitedResultSet(mockResultSet, -1, true, TEST_QUERY);

        QueryBlockedException exception =
                assertThrows(QueryBlockedException.class, () -> limitedResultSet.next());
        assertEquals(1L, exception.getRowsCount());
        assertEquals(-1, exception.getLimit());
        assertEquals(TEST_QUERY, exception.getQuery());
        assertEquals(1, limitedResultSet.getRowsCount());

        verify(mockResultSet, times(1)).next();
    }

    @Test
    void testNextWithLargeLimit() throws SQLException {
        // Setup: mock resultSet to return true for many calls
        when(mockResultSet.next()).thenReturn(true, true, true, true, true, false);
        
        limitedResultSet = new LimitedResultSet(mockResultSet, 1000, true, TEST_QUERY);

        // All calls should succeed within the large limit
        for (int i = 0; i < 5; i++) {
            assertTrue(limitedResultSet.next());
            assertEquals(i + 1, limitedResultSet.getRowsCount());
        }

        // Last call should return false
        assertFalse(limitedResultSet.next());
        assertEquals(5, limitedResultSet.getRowsCount());

        // Verify that next() was called 6 times on the underlying ResultSet
        verify(mockResultSet, times(6)).next();
    }

    @Test
    void testNextWithEmptyResultSet() throws SQLException {
        // Setup: mock resultSet to return false immediately
        when(mockResultSet.next()).thenReturn(false);
        
        limitedResultSet = new LimitedResultSet(mockResultSet, 10, true, TEST_QUERY);

        // First call should return false
        assertFalse(limitedResultSet.next());
        assertEquals(0, limitedResultSet.getRowsCount());

        // Verify that next() was called 1 time on the underlying ResultSet
        verify(mockResultSet, times(1)).next();
    }

    @Test
    void testNextWithSQLExceptionFromUnderlyingResultSet() throws SQLException {
        // Setup: mock resultSet to throw SQLException
        when(mockResultSet.next()).thenThrow(new SQLException("Database error"));
        
        limitedResultSet = new LimitedResultSet(mockResultSet, 10, true, TEST_QUERY);

        // Should propagate the SQLException from underlying ResultSet
        SQLException exception = assertThrows(SQLException.class, () -> limitedResultSet.next());
        assertEquals("Database error", exception.getMessage());
        assertEquals(0, limitedResultSet.getRowsCount());

        // Verify that next() was called 1 time on the underlying ResultSet
        verify(mockResultSet, times(1)).next();
    }

    @Test
    void testRowsCountIncrementOnlyOnSuccessfulNext() throws SQLException {
        // Setup: mock resultSet to return true, then false
        when(mockResultSet.next()).thenReturn(true, false);
        
        limitedResultSet = new LimitedResultSet(mockResultSet, 5, true, TEST_QUERY);

        // First call succeeds, count should increment
        assertTrue(limitedResultSet.next());
        assertEquals(1, limitedResultSet.getRowsCount());

        // Second call fails (returns false), count should not increment
        assertFalse(limitedResultSet.next());
        assertEquals(1, limitedResultSet.getRowsCount());

        // Verify that next() was called 2 times on the underlying ResultSet
        verify(mockResultSet, times(2)).next();
    }

    @Test
    void testMultipleCallsAfterLimitExceededInAlertingMode() throws SQLException {
        // Setup: mock resultSet to return true for many calls
        when(mockResultSet.next()).thenReturn(true, true, true, true, true, true, false);
        
        limitedResultSet = new LimitedResultSet(mockResultSet, 3, false, TEST_QUERY);

        // Calls within limit
        assertTrue(limitedResultSet.next());
        assertEquals(1, limitedResultSet.getRowsCount());

        assertTrue(limitedResultSet.next());
        assertEquals(2, limitedResultSet.getRowsCount());

        assertTrue(limitedResultSet.next());
        assertEquals(3, limitedResultSet.getRowsCount());

        // Calls exceeding limit - should continue without throwing
        assertTrue(limitedResultSet.next());
        assertEquals(4, limitedResultSet.getRowsCount());

        assertTrue(limitedResultSet.next());
        assertEquals(5, limitedResultSet.getRowsCount());

        assertTrue(limitedResultSet.next());
        assertEquals(6, limitedResultSet.getRowsCount());

        // End of result set
        assertFalse(limitedResultSet.next());
        assertEquals(6, limitedResultSet.getRowsCount());

        // Verify that next() was called 7 times on the underlying ResultSet
        verify(mockResultSet, times(7)).next();
    }

    @Test
    void testGetters() {
        limitedResultSet = new LimitedResultSet(mockResultSet, 42, true, TEST_QUERY);
        
        assertEquals(42, limitedResultSet.getLimit());
        assertTrue(limitedResultSet.isToBlock());
        assertEquals(0, limitedResultSet.getRowsCount());
    }

    @Test
    void testGettersWithAlertingMode() {
        limitedResultSet = new LimitedResultSet(mockResultSet, 99, false, TEST_QUERY);
        
        assertEquals(99, limitedResultSet.getLimit());
        assertFalse(limitedResultSet.isToBlock());
        assertEquals(0, limitedResultSet.getRowsCount());
    }
}
