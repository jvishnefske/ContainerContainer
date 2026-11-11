package org.artifactory.ui.rest.model.artifacts.search.syntax;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Amir Avidan
 */
class DateRangeTest {

    @Test
    void isEmptyShouldReturnTrueWhenBothFromAndToAreNull() {
        DateRange dateRange = new DateRange(null, null);

        assertTrue(dateRange.isEmpty());
    }

    @Test
    void isEmptyShouldReturnTrueWhenBothFromAndToAreZero() {
        DateRange dateRange = new DateRange(0L, 0L);

        assertTrue(dateRange.isEmpty());
    }

    @Test
    void isEmptyShouldReturnTrueWhenFromIsZeroAndToIsNull() {
        DateRange dateRange = new DateRange(0L, null);

        assertTrue(dateRange.isEmpty());
    }

    @Test
    void isEmptyShouldReturnTrueWhenFromIsNullAndToIsZero() {
        DateRange dateRange = new DateRange(null, 0L);

        assertTrue(dateRange.isEmpty());
    }

    @Test
    void isEmptyShouldReturnFalseWhenOnlyFromHasValue() {
        DateRange dateRange = new DateRange(1640995200000L, null);

        assertFalse(dateRange.isEmpty());
    }

    @Test
    void isEmptyShouldReturnFalseWhenOnlyToHasValue() {
        DateRange dateRange = new DateRange(null, 1640995200000L);

        assertFalse(dateRange.isEmpty());
    }

    @Test
    void isEmptyShouldReturnFalseWhenBothFromAndToHaveValues() {
        DateRange dateRange = new DateRange(1640995200000L, 1672531200000L);

        assertFalse(dateRange.isEmpty());
    }

    @Test
    void hasFromShouldReturnFalseWhenFromIsNull() {
        DateRange dateRange = new DateRange(null, 1640995200000L);

        assertFalse(dateRange.hasFrom());
    }

    @Test
    void hasFromShouldReturnFalseWhenFromIsZero() {
        DateRange dateRange = new DateRange(0L, 1640995200000L);

        assertFalse(dateRange.hasFrom());
    }

    @Test
    void hasFromShouldReturnTrueWhenFromHasPositiveValue() {
        DateRange dateRange = new DateRange(1640995200000L, null);

        assertTrue(dateRange.hasFrom());
    }

    @Test
    void hasToShouldReturnFalseWhenToIsNull() {
        DateRange dateRange = new DateRange(1640995200000L, null);

        assertFalse(dateRange.hasTo());
    }

    @Test
    void hasToShouldReturnFalseWhenToIsZero() {
        DateRange dateRange = new DateRange(1640995200000L, 0L);

        assertFalse(dateRange.hasTo());
    }

    @Test
    void hasToShouldReturnTrueWhenToHasPositiveValue() {
        DateRange dateRange = new DateRange(null, 1640995200000L);

        assertTrue(dateRange.hasTo());
    }

    @Test
    void hasFromShouldReturnTrueWhenBothFromAndToHaveValues() {
        DateRange dateRange = new DateRange(1640995200000L, 1672531200000L);

        assertTrue(dateRange.hasFrom());
    }

    @Test
    void hasToShouldReturnTrueWhenBothFromAndToHaveValues() {
        DateRange dateRange = new DateRange(1640995200000L, 1672531200000L);

        assertTrue(dateRange.hasTo());
    }
}

