package org.artifactory.version.v215;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import static org.artifactory.version.v215.LogbackMetricsLogsConverter.METRICS_APPENDER_NAME;
import static org.artifactory.version.v215.LogbackMetricsLogsConverter.METRICS_EVENTS_APPENDER_NAME;
import static org.artifactory.version.v215.LogbackMetricsLogsConverter.METRICS_EVENTS_LOGGER_NAME;
import static org.artifactory.version.v215.LogbackMetricsLogsConverter.METRICS_LOGGER_NAME;

class LogbackMetricsLogsConverterTest extends LogbackConverterTest {

    @Test
    void testMetricsAppendersAndLoggers() throws Exception {
        Document doc = convertXml("/org/artifactory/logging/version/v8/logback_before.xml",
                new LogbackMetricsLogsConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();

        assertLoggerExists(root, ns, METRICS_LOGGER_NAME);
        assertAppenderExists(root, ns, METRICS_APPENDER_NAME);
        assertAppenderExists(root, ns, METRICS_EVENTS_APPENDER_NAME);
        assertLoggerExists(root, ns, METRICS_EVENTS_LOGGER_NAME);
    }

}