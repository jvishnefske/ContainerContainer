package org.artifactory.logging.layout.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import io.vavr.collection.Array;
import org.artifactory.logging.LoggingTestUtils;
import org.artifactory.logging.SaasLogNames;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Tomer Slutsky
 */
class ServiceJsonLayoutTest {
    ServiceJsonLayout serviceJsonLayout = new ServiceJsonLayout();

    @Test
    void testDoLayout() {
        LoggingEvent event = LoggingTestUtils
                .createEvent(Array.of(), ServiceJsonLayout.class);
        event.setCallerData(Array.of(new StackTraceElement(this.getClass().getName(),
                "testMethod", "ServiceJsonLayoutTest.java", 1)).asJava().toArray(new StackTraceElement[0]));
        String s = serviceJsonLayout.doLayout(event);
        Assertions.assertTrue(s.contains("\"" + SaasLogNames.SERVICE_LOG.getLogKey() + "\":\"" +
                SaasLogNames.SERVICE_LOG.getLogName() + "\""));
        Assertions.assertTrue(s.contains("\"traceid\":\"traceid\""));
        Assertions.assertTrue(s.contains("\"service\":\"jfrt\""));
        Assertions.assertTrue(s.contains("\"loglevel\":\"INFO\""));
        Assertions.assertTrue(s.contains("\"class\":\"org.artifactory.logging.layout.service.ServiceJsonLayoutTest\""));
        Assertions.assertTrue(s.contains("\"line\":1"));
        Assertions.assertTrue(s.contains("\"thread\":\"main\""));
        Assertions.assertTrue(s.contains("\"app\":{"));
        Assertions.assertTrue(s.contains("\"message\":\"\""));
        Assertions.assertTrue(s.contains("\"datetime\":"));
    }

    @Test
    void testException() {
        LoggingEvent event = LoggingTestUtils.createEvent(Array.of(), ServiceJsonLayout.class,
                Level.ERROR,
                Map.of("uber-trace-id", "traceid", "act.kind", "HTTP")
        );
        event.setCallerData(Array.of(new StackTraceElement(this.getClass().getName(),
                "testMethod", "ServiceJsonLayoutTest.java", 1)).asJava().toArray(new StackTraceElement[0]));
        event.setThrowableProxy(new ThrowableProxy(new IllegalArgumentException("bad param")));

        String actual = serviceJsonLayout.doLayout(event);
        Assertions.assertTrue(actual.contains(
                "\"exception\":{\"msg\":\"bad param\",\"type\":\"java.lang.IllegalArgumentException\",\"stack\":\"\"}"
        ));
        Assertions.assertTrue(actual.contains("\"mdc\":{\"act_kind\":\"HTTP\"}"));
    }

    @Test
    void testCopyJsonFieldsCallerData() {
        LoggingEvent event = LoggingTestUtils.createEvent(Array.of(), ServiceJsonLayout.class,
                Level.ERROR,
                Map.of("copy-trace-id", "id", "act.kind", "HTTP")
        );
        event.setLoggerName("org.artifactory.addon.docker.rest.v2.handler.DockerV2RemoteGetTagsHandler");
        event.setCallerData(new StackTraceElement[] {
                new StackTraceElement("org.jfrog.common.ExceptionLogUtils", "debugOrWarn", "ExceptionLogUtils.java",
                        50),
                new StackTraceElement("org.artifactory.addon.docker.rest.v2.handler.DockerV2RemoteGetTagsHandler",
                        "parseAndSliceTagsCatalog", "DockerV2RemoteGetTagsHandler.java", 222)
        });

        // Format the log event
        Map<String, Object> fields = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", "2023-10-10T10:10:10.123Z");
        data.put("level", "ERROR");
        data.put("formattedMessage", "Test message");
        data.put("thread", "main");

        serviceJsonLayout.copyJsonFields(fields, data, event);
        String logOutput = serviceJsonLayout.doLayout(event);

        // Verify that the correct class name is captured
        Assertions.assertTrue(logOutput.contains(
                "\"class\":\"org.artifactory.addon.docker.rest.v2.handler.DockerV2RemoteGetTagsHandler\""));
        Assertions.assertTrue(logOutput.contains("\"line\":222"));
    }
}