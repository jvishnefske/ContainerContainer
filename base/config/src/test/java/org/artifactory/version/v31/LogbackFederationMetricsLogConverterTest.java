package org.artifactory.version.v31;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

/**
 * @author Oleksandr Legenkiy
 */
class LogbackFederationMetricsLogConverterTest extends LogbackConverterTest {

    @Test
    void testFederationMetricsAppendersAndLoggers() throws Exception {
        Document doc = convertXml("/org/artifactory/logging/version/v32/before_federation_metrics_log.xml",
                new LogbackFederationMetricsLogConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        assertAppenderExists(root, ns, LogbackFederationMetricsLogConverter.APPENDER_NAME);
        assertLoggerExists(root, ns, LogbackFederationMetricsLogConverter.LOGGER_NAME);
        assertAppenderExists(root, ns, LogbackFederationMetricsLogConverter.EVENTS_APPENDER_NAME);
        assertLoggerExists(root, ns, LogbackFederationMetricsLogConverter.EVENTS_LOGGER_NAME);
    }
}
