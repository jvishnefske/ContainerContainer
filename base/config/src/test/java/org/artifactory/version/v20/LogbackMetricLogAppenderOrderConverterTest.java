package org.artifactory.version.v20;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.junit.jupiter.api.Test;

import static org.artifactory.version.v20.LogbackMetricLogAppenderOrderConverter.APPENDER_NAME;
import static org.artifactory.version.v20.LogbackMetricLogAppenderOrderConverter.LOGGER_NAME;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogbackMetricLogAppenderOrderConverterTest extends LogbackConverterTest {

    @Test
    void test() throws Exception {
        Document doc = convertXml("/org/artifactory/logging/version/v20/metrics-wrong-order.xml",
                new LogbackMetricLogAppenderOrderConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();

        assertAppenderExists(root, ns, APPENDER_NAME);
        assertLoggerExists(root, ns, LOGGER_NAME);

        XMLOutputter xmlOutputter = new XMLOutputter(Format.getPrettyFormat());
        String content = xmlOutputter.outputString(root);
        int metricsLoggerIndex = content.indexOf("<logger name=\"METRICS\"");
        int metricsAppenderIndex = content.indexOf("<appender name=\"METRICS-APPENDER\"");

        assertTrue(metricsAppenderIndex < metricsLoggerIndex);
    }

}
