package org.artifactory.util;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.util.StopWatch;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StopWatchUtilTest {

    @Test
    void measureTime_withCallable() throws Exception {
        // Given
        StopWatch stopWatch = new StopWatch();
        Callable<String> callable = () -> "result";

        // When
        String result = StopWatchUtil.measureTime(stopWatch, callable);

        // Then
        assertEquals("result", result);
        assertEquals(1, stopWatch.getTaskCount());
    }

    @Test
    void measureTime_withRunnable() {
        // Given
        StopWatch stopWatch = new StopWatch();
        Runnable runnable = mock(Runnable.class);

        // When
        StopWatchUtil.measureTime(stopWatch, runnable);

        // Then
        verify(runnable).run();
        assertEquals(1, stopWatch.getTaskCount());
    }

    @Test
    void measureAndLogTime() {
        // Given
        Logger logger = spy(LoggerFactory.getLogger(this.getClass()));
        Runnable runnable = mock(Runnable.class);
        Level level = Level.INFO;
        String logTemplate = "Execution time: {} ms";

        // When
        StopWatchUtil.measureAndLogTime(logger, level, logTemplate, runnable);

        // Then
        verify(runnable).run();
        verify(logger).info(eq(logTemplate), anyLong());
    }

    @Test
    void measureTime_withCallable_throwsException() {
        // Given
        StopWatch stopWatch = mock(StopWatch.class);
        when(stopWatch.isRunning()).thenReturn(true);
        Callable<String> callable = () -> {
            throw new Exception("Test exception");
        };

        // When / Then
        assertThrows(Exception.class, () -> StopWatchUtil.measureTime(stopWatch, callable));
        verify(stopWatch).stop();
    }

    @Test
    void measureTime_withRunnable_throwsException() {
        // Given
        StopWatch stopWatch = new StopWatch();
        Runnable runnable = () -> {
            throw new RuntimeException("Test exception");
        };

        // When / Then
        assertThrows(RuntimeException.class, () -> StopWatchUtil.measureTime(stopWatch, runnable));
        assertEquals(1, stopWatch.getTaskCount());
    }
}