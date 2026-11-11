package org.artifactory.devopsagent.errorcatalog.ingestion;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class ErrorCatalogLogbackAppenderTest extends ArtifactoryHomeBoundTest {

    private ErrorCatalogEventBuffer buffer;
    private ErrorCatalogLogbackAppender appender;

    @BeforeMethod
    public void setUp() {
        buffer = new ErrorCatalogEventBuffer(100, 100);
        ErrorCatalogLogbackAppender.installBuffer(buffer);
        appender = new ErrorCatalogLogbackAppender();
        appender.setContext(new ch.qos.logback.classic.LoggerContext());
        appender.start();
        // Flip the feature flag on for the duration of the test via the ArtifactoryHome-bound properties.
        homeStub.setProperty(ConstantValues.devopsTroubleshootingEnabled, "true");
    }

    @AfterMethod
    public void tearDown() {
        appender.stop();
        ErrorCatalogLogbackAppender.uninstallBuffer();
        homeStub.setProperty(ConstantValues.devopsTroubleshootingEnabled, "false");
    }

    @Test
    public void testWarnAndErrorEventsAreCaptured() {
        appender.doAppend(parameterizedEvent(Level.ERROR, "code path A"));
        appender.doAppend(parameterizedEvent(Level.WARN, "code path B"));
        ErrorCatalogEventBuffer.DrainResult result = buffer.drain();
        assertEquals(result.rawEvents().size(), 2);
        assertEquals(result.aggregated().size(), 2);
    }

    @Test
    public void testInfoAndDebugAreFilteredOut() {
        appender.doAppend(parameterizedEvent(Level.INFO, "info"));
        appender.doAppend(parameterizedEvent(Level.DEBUG, "debug"));
        ErrorCatalogEventBuffer.DrainResult result = buffer.drain();
        assertEquals(result.rawEvents().size(), 0);
    }

    @Test
    public void testDoesNotCaptureWhenFeatureFlagOff() {
        homeStub.setProperty(ConstantValues.devopsTroubleshootingEnabled, "false");
        appender.doAppend(parameterizedEvent(Level.ERROR, "should be ignored"));
        ErrorCatalogEventBuffer.DrainResult result = buffer.drain();
        assertEquals(result.rawEvents().size(), 0);
    }

    @Test
    public void testSanitizesBeforeBuffering() {
        appender.doAppend(parameterizedEvent(Level.ERROR, "Authorization: Bearer abc.def-123_XYZ"));
        ErrorCatalogEventBuffer.DrainResult result = buffer.drain();
        assertEquals(result.rawEvents().size(), 1);
        String raw = result.rawEvents().get(0).rawMessage();
        // Sanitizer should have replaced the token.
        org.testng.Assert.assertFalse(raw.contains("abc.def-123_XYZ"));
    }

    @Test
    public void testEventsBeforeBufferInstalledIncrementDroppedCounter() {
        ErrorCatalogLogbackAppender.uninstallBuffer();
        long droppedBefore = ErrorCatalogLogbackAppender.droppedNoBufferCount();

        appender.doAppend(parameterizedEvent(Level.ERROR, "before-wiring"));
        appender.doAppend(parameterizedEvent(Level.WARN, "also-before-wiring"));

        long droppedAfter = ErrorCatalogLogbackAppender.droppedNoBufferCount();
        assertEquals(droppedAfter - droppedBefore, 2L,
                "two capturable events with no buffer installed must advance the dropped counter by 2");
        ErrorCatalogLogbackAppender.installBuffer(buffer);
    }

    @Test
    public void testAppenderSwallowsRuntimeExceptionFromExtraction() {
        LoggingEvent broken = new LoggingEvent();
        broken.setLevel(Level.ERROR);
        broken.setLoggerName("com.example.Broken");
        broken.setMessage(null);
        broken.setTimeStamp(System.currentTimeMillis());

        appender.doAppend(broken);

        appender.doAppend(parameterizedEvent(Level.ERROR, "after-broken-event"));
        ErrorCatalogEventBuffer.DrainResult result = buffer.drain();
        assertTrue(result.rawEvents().size() >= 1,
                "appender must keep functioning after swallowing a RuntimeException");
    }

    private static LoggingEvent parameterizedEvent(Level level, String message) {
        LoggingEvent ev = new LoggingEvent();
        ev.setLevel(level);
        ev.setLoggerName("com.example.Foo");
        ev.setMessage(message);
        ev.setTimeStamp(System.currentTimeMillis());
        return ev;
    }
}
