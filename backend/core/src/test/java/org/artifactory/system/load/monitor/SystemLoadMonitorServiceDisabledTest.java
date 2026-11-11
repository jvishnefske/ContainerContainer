package org.artifactory.system.load.monitor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.artifactory.system.load.event.SystemLoadState.HEALTHY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class SystemLoadMonitorServiceDisabledTest {

    @Test
    void testGetSystemLoad_AlwaysReturnHealthy() {
        SystemLoadProvider systemLoadProvider = new SystemLoadMonitorServiceDisabled();
        assertEquals(HEALTHY, systemLoadProvider.getSystemLoadState());
    }

    @Test
    void testRegisterListener_NoException() {
        InternalSystemLoadMonitorService systemLoadMonitorService = new SystemLoadMonitorServiceDisabled();
        assertDoesNotThrow(() -> systemLoadMonitorService.registerListener(null));
    }

    @Test
    void testMonitorSystemLoad_PrintWarningLog() {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        Logger logger = (Logger) LoggerFactory.getLogger(SystemLoadMonitorServiceDisabled.class);
        logger.setLevel(Level.INFO);
        logger.addAppender(listAppender);
        listAppender.start();

        SystemLoadMonitorService systemLoadMonitorService = new SystemLoadMonitorServiceDisabled();
        systemLoadMonitorService.monitorSystemLoad();

        List<ILoggingEvent> logs = listAppender.list;

        ILoggingEvent loggingEvent = logs.getFirst();
        assertEquals(Level.INFO, loggingEvent.getLevel());
        assertEquals(
                "System load monitoring is disabled. No events will be triggered for listeners, and the system load " +
                        "state will always be reported as healthy",
                loggingEvent.getMessage());

        ILoggingEvent iLoggingEvent2 = logs.get(1);
        assertEquals(Level.WARN, iLoggingEvent2.getLevel());
        assertEquals("Call to monitorSystemLoad() when System Load Monitor is disabled", iLoggingEvent2.getMessage());
    }
}