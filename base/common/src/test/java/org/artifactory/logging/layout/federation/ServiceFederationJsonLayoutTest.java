package org.artifactory.logging.layout.federation;

import ch.qos.logback.classic.spi.LoggingEvent;
import io.vavr.collection.Array;
import org.artifactory.logging.LoggingTestUtils;
import org.artifactory.logging.SaasLogNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * @author Alexei Vainshtein
 */
class ServiceFederationJsonLayoutTest {
    @AfterEach
    void cleanUp() {
        MDC.clear();
    }

    @Test
    void testAddCustomDataToJsonMap() {
        ServiceFederationJsonLayout serviceFederationJsonLayout = new ServiceFederationJsonLayout();
        MDC.put("uber-trace-id", "traceid");
        LoggingEvent event = LoggingTestUtils
                .createEvent(Array.of(), ServiceFederationJsonLayout.class);
        event.setCallerData(Array.of(new StackTraceElement(this.getClass().getName(),
                "testMethod", "ServiceFederationJsonLayoutTest.java", 1)).asJava().toArray(new StackTraceElement[0]));
        String s = serviceFederationJsonLayout.doLayout(event);
        Assertions.assertTrue(s.contains("\"" + SaasLogNames.SERVICE_FEDERATION_LOG.getLogKey() + "\":\"" +
                SaasLogNames.SERVICE_FEDERATION_LOG.getLogName() + "\""));
        Assertions.assertTrue(s.contains("\"traceid\":\"traceid\""));
        Assertions.assertTrue(s.contains("\"service\":\"jfrt\""));
        Assertions.assertTrue(s.contains("\"loglevel\":\"INFO\""));
        Assertions.assertTrue(s.contains("\"class\":\"ServiceFederationJsonLayoutTest.java\""));
        Assertions.assertTrue(s.contains("\"line\":\"1\""));
        Assertions.assertTrue(s.contains("\"thread\":\"main\""));
        Assertions.assertTrue(s.contains("\"service_federation\":{"));
        Assertions.assertTrue(s.contains("\"message\":\"\""));
        Assertions.assertTrue(s.contains("\"datetime\":"));
    }
}