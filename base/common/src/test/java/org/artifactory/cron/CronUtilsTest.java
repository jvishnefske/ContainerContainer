package org.artifactory.cron;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

public class CronUtilsTest {

    @ParameterizedTest
    @MethodSource("cron")
    public void testGetNextExecutionTime(String cronExpression, Optional<Long> time) {
        Optional<Long> actual = CronUtils.getNextExecutionTime(cronExpression);
        assertEquals(actual, time);
    }

    @Test
    public void testGetNextExecutionAfterTime() {
        Optional<Long> actual = CronUtils.getNextExecutionAfterTime("0 15 10 * * ?", System.currentTimeMillis());
        assertNotNull(actual.get());
    }

    private static Stream<Arguments> cron() {
        return Stream.of(
                Arguments.of("0 15 10 * * ? 2005", Optional.empty()),
                Arguments.of("", Optional.empty()),
                Arguments.of(null, Optional.empty()),
                Arguments.of("invalid", Optional.empty())
        );
    }

    @Test
    public void testPolicyScheduling_DailyCronExpression() {
        String dailyCronExpression = "0 0 2 * * ?";
        assertTrue(CronUtils.isValid(dailyCronExpression));
        Date nextExecution = CronUtils.getNextExecution(dailyCronExpression);
        assertNotNull(nextExecution);
        assertTrue(nextExecution.getTime() > System.currentTimeMillis());
        Optional<Long> nextExecutionTime = CronUtils.getNextExecutionTime(dailyCronExpression);
        assertTrue(nextExecutionTime.isPresent());
        assertEquals(nextExecution.getTime(), nextExecutionTime.get().longValue());
    }

    @Test
    public void testPolicyScheduling_WeeklyCronExpression() {
        String weeklyCronExpression = "0 30 3 ? * SUN";
        assertTrue(CronUtils.isValid(weeklyCronExpression));
        Date nextExecution = CronUtils.getNextExecution(weeklyCronExpression);
        assertNotNull(nextExecution);
        assertTrue(nextExecution.getTime() > System.currentTimeMillis());
        Optional<Long> nextExecutionTime = CronUtils.getNextExecutionTime(weeklyCronExpression);
        assertTrue(nextExecutionTime.isPresent());
    }

    @Test
    public void testPolicyScheduling_MonthlyCronExpression() {
        String monthlyCronExpression = "0 0 1 1 * ?";
        assertTrue(CronUtils.isValid(monthlyCronExpression));
        Date nextExecution = CronUtils.getNextExecution(monthlyCronExpression);
        assertNotNull(nextExecution);
        assertTrue(nextExecution.getTime() > System.currentTimeMillis());
    }

    @Test
    public void testPolicyScheduling_FrequentCronExpression() {
        String frequentCronExpression = "0 */5 * * * ?"; // Every 5 minutes
        assertTrue(CronUtils.isValid(frequentCronExpression));
        Date nextExecution = CronUtils.getNextExecution(frequentCronExpression);
        assertNotNull(nextExecution);
        assertTrue(nextExecution.getTime() > System.currentTimeMillis());
        long fiveMinutesFromNow = System.currentTimeMillis() + (5 * 60 * 1000);
        assertTrue(nextExecution.getTime() <= fiveMinutesFromNow);
    }

    @Test
    public void testPolicyScheduling_InvalidCronHandling() {
        String invalidCronExpression = "not a valid cron";
        assertFalse(CronUtils.isValid(invalidCronExpression));
        Optional<Long> nextExecutionTime = CronUtils.getNextExecutionTime(invalidCronExpression);
        assertFalse(nextExecutionTime.isPresent());
    }

    @Test
    public void testPolicyScheduling_CompareWithCurrentExecution() {
        String cronExpression = "0 */10 * * * ?"; // Every 10 minutes
        Date nextExecution1 = CronUtils.getNextExecution(cronExpression);
        Date nextExecution2 = CronUtils.getNextExecutionAfter(cronExpression, nextExecution1.getTime());
        assertNotNull(nextExecution1);
        assertNotNull(nextExecution2);
        assertTrue(nextExecution2.getTime() > nextExecution1.getTime());
        long timeDifference = nextExecution2.getTime() - nextExecution1.getTime();
        long tenMinutesInMillis = 10 * 60 * 1000;
        assertTrue(Math.abs(timeDifference - tenMinutesInMillis) < 1000);
    }
}
