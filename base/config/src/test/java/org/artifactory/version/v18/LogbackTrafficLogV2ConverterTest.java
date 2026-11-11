package org.artifactory.version.v18;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import static org.artifactory.version.v18.LogbackTrafficLogV2Converter.APPENDER_NAME;
import static org.artifactory.version.v18.LogbackTrafficLogV2Converter.LOGGER_NAME;

class LogbackTrafficLogV2ConverterTest extends LogbackConverterTest {

    @Test
    void testTrafficLogV2AppendersAndLoggers() throws Exception {
        Document doc = convertXml("/org/artifactory/logging/version/v18/before_traffic_log_v2.xml",
                new LogbackTrafficLogV2Converter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();

        assertAppenderExists(root, ns, APPENDER_NAME);
        assertLoggerExists(root, ns, LOGGER_NAME);
    }

}