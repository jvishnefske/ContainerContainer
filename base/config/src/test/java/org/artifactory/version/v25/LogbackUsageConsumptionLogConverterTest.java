package org.artifactory.version.v25;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import static org.artifactory.version.v25.LogbackUsageConsumptionLogConverter.APPENDER_NAME;
import static org.artifactory.version.v25.LogbackUsageConsumptionLogConverter.LOGGER_NAME;

/**
 * @author Andrey Tashlitsky
 */
class LogbackUsageConsumptionLogConverterTest extends LogbackConverterTest {

    @Test
    void testUsageConsumptionLogAppendersAndLoggers() throws Exception {
        Document doc = convertXml("/org/artifactory/logging/version/v25/before_usage_consumption_log.xml",
                new LogbackUsageConsumptionLogConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        assertAppenderExists(root, ns, APPENDER_NAME);
        assertLoggerExists(root, ns, LOGGER_NAME);
    }
}
