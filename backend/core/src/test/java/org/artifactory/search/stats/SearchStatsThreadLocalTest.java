package org.artifactory.search.stats;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.artifactory.api.search.artifact.ArtifactSearchControls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Andrey Tashlitsky
 */
class SearchStatsThreadLocalTest {

    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(SearchStatsThreadLocal.class);
        logger.detachAndStopAllAppenders();
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void executeWithLogging_shouldPopulateStatsAndLog_whenAllFieldsAreSet() {
        // Arrange
        ArtifactSearchControls controls = new ArtifactSearchControls();

        // Act
        SearchStatsThreadLocal.executeWithLogging(() -> {
            SearchStatsThreadLocal.setSearchType("artifact-quick");
            SearchStatsThreadLocal.addDbDuration(150L);
            SearchStatsThreadLocal.addDbResults(100L);
            SearchStatsThreadLocal.setReturnedResults(50L);
            SearchStatsThreadLocal.setSearchControls(controls);
        });

        // Assert
        assertEquals(1, logAppender.list.size());
        ILoggingEvent logEvent = logAppender.list.getFirst();
        assertEquals(Level.INFO, logEvent.getLevel());
        String logMessage = logEvent.getFormattedMessage();
        assertTrue(logMessage.contains("search-type=artifact-quick"));
        assertTrue(logMessage.contains("db-duration=150ms"));
        assertTrue(logMessage.contains("db-results=100"));
        assertTrue(logMessage.contains("returned-results=50"));
        assertTrue(logMessage.contains("search-controls="));
    }

    @Test
    void executeWithLogging_shouldClearThreadLocal_whenActionCompletes() {
        // Act
        SearchStatsThreadLocal.executeWithLogging(() -> {
            SearchStatsThreadLocal.setSearchType("test");
        });

        logAppender.list.clear();
        SearchStatsThreadLocal.executeWithLogging(() -> {
            SearchStatsThreadLocal.setSearchType("new-test");
        });

        // Assert
        assertEquals(1, logAppender.list.size());
        String logMessage = logAppender.list.getFirst().getFormattedMessage();
        assertTrue(logMessage.contains("search-type=new-test"));
        assertTrue(logMessage.contains("db-duration=0"));
    }

    @Test
    void executeWithLogging_shouldLogAndClear_whenActionThrowsException() {
        // Arrange
        RuntimeException expectedException = new RuntimeException("Test exception");

        // Act
        RuntimeException thrown = null;
        try {
            SearchStatsThreadLocal.executeWithLogging(() -> {
                SearchStatsThreadLocal.setSearchType("error-test");
                SearchStatsThreadLocal.addDbDuration(200L);
                throw expectedException;
            });
        } catch (RuntimeException e) {
            thrown = e;
        }

        // Assert
        assertEquals(expectedException, thrown);
        assertEquals(1, logAppender.list.size());
        String logMessage = logAppender.list.getFirst().getFormattedMessage();
        assertTrue(logMessage.contains("search-type=error-test"));
        assertTrue(logMessage.contains("db-duration=200ms"));
    }

    @Test
    void executeWithLogging_shouldIsolateThreadLocalBetweenThreads_whenExecutedConcurrently() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(2);

        // Act
        Thread thread1 = new Thread(() -> {
            SearchStatsThreadLocal.executeWithLogging(() -> {
                SearchStatsThreadLocal.setSearchType("thread1-search");
                SearchStatsThreadLocal.addDbDuration(100L);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            latch.countDown();
        });

        Thread thread2 = new Thread(() -> {
            SearchStatsThreadLocal.executeWithLogging(() -> {
                SearchStatsThreadLocal.setSearchType("thread2-search");
                SearchStatsThreadLocal.addDbDuration(200L);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            latch.countDown();
        });

        thread1.start();
        thread2.start();
        latch.await();

        // Assert
        assertEquals(2, logAppender.list.size());
        String allLogs = logAppender.list.get(0).getFormattedMessage() + logAppender.list.get(1).getFormattedMessage();
        assertTrue(allLogs.contains("thread1-search"));
        assertTrue(allLogs.contains("thread2-search"));
        assertTrue(allLogs.contains("db-duration=100ms"));
        assertTrue(allLogs.contains("db-duration=200ms"));
    }

    @Test
    void setters_shouldHandleNullThreadLocal_whenCalledOutsideExecuteWithLogging() {
        // Act
        SearchStatsThreadLocal.setSearchType("test");
        SearchStatsThreadLocal.addDbDuration(100L);
        SearchStatsThreadLocal.addDbResults(50L);
        SearchStatsThreadLocal.setReturnedResults(25L);
        SearchStatsThreadLocal.setSearchControls(new ArtifactSearchControls());

        // Assert
        assertEquals(0, logAppender.list.size());
    }

    @Test
    void executeWithLogging_shouldLogNullValues_whenNoFieldsAreSet() {
        // Act
        SearchStatsThreadLocal.executeWithLogging(() -> {
            // Do nothing - leave all fields as default
        });

        // Assert
        assertEquals(1, logAppender.list.size());
        String logMessage = logAppender.list.getFirst().getFormattedMessage();
        assertTrue(logMessage.contains("search-type=null"));
        assertTrue(logMessage.contains("db-duration=0"));
        assertTrue(logMessage.contains("db-results=0"));
        assertTrue(logMessage.contains("returned-results=0"));
        assertTrue(logMessage.contains("search-controls=null"));
    }

    @Test
    void addDbDurationAndDbResults_shouldAccumulateBoth_whenCalledMultipleTimes() {
        // Act
        SearchStatsThreadLocal.executeWithLogging(() -> {
            SearchStatsThreadLocal.setSearchType("checksum-search");
            SearchStatsThreadLocal.addDbDuration(50L);
            SearchStatsThreadLocal.addDbResults(100L);
            SearchStatsThreadLocal.addDbDuration(75L);
            SearchStatsThreadLocal.addDbResults(150L);
        });

        // Assert
        assertEquals(1, logAppender.list.size());
        String logMessage = logAppender.list.getFirst().getFormattedMessage();
        assertTrue(logMessage.contains("search-type=checksum-search"));
        assertTrue(logMessage.contains("db-duration=125ms"));
        assertTrue(logMessage.contains("db-results=250"));
    }

    @Test
    void logSearchStats_shouldHandleNullStats_whenThreadLocalIsEmpty() {
        // Act
        ReflectionTestUtils.invokeMethod(SearchStatsThreadLocal.class, "logSearchStats");

        // Assert
        assertEquals(0, logAppender.list.size());
    }
}
