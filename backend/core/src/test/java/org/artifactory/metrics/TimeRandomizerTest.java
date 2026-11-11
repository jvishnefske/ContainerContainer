package org.artifactory.metrics;

import org.junit.jupiter.api.RepeatedTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for TimeRandomizer utility class
 *
 * @author Evgeny Kurtser
 */
class TimeRandomizerTest {

    @RepeatedTest(100)
    void testRandomMinute_ReturnsValueInValidRange() {
        // When
        int minute = TimeRandomizer.randomMinute();

        // Then
        assertTrue(minute >= 0 && minute < 60, "Minute should be between 0-59, got: " + minute);
    }

    @RepeatedTest(100)
    void testRandomHourAtNight_ReturnsValueInValidRange() {
        // When
        int hour = TimeRandomizer.randomHourAtNight();

        // Then
        assertTrue(hour >= 0 && hour < 4, "Hour at night should be between 0-3, got: " + hour);
    }
}