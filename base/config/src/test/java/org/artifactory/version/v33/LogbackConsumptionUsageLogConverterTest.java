package org.artifactory.version.v33;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

/**
 * @author BarakH
 */
class LogbackConsumptionUsageLogConverterTest extends LogbackConverterTest {

    @Test
    void testLogbackConsumptionUsageLogConverter() throws Exception {
        Document doc = convertXml("/org/artifactory/logging/version/v32/before_federation_metrics_log.xml",
                new LogbackConsumptionUsageLogConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        assertLoggerExists(root, ns, LogbackConsumptionUsageLogConverter.LOGGER_NAME);
        assertAppenderExists(root, ns, LogbackConsumptionUsageLogConverter.APPENDER_NAME);
    }
}
