package org.artifactory.version.v46;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

/**
 * @author Dvir Twina
 */
class LogbackTrafficLogV3ConverterTest extends LogbackConverterTest {

    @Test
    void testTrafficLogV3AppendersAndLoggers() throws Exception {

        Document doc = convertXml("/org/artifactory/logging/version/v46/before_traffic_log_v3.xml",
                new LogbackTrafficLogV3Converter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();

        assertAppenderExists(root, ns, LogbackTrafficLogV3Converter.APPENDER_NAME);
        assertLoggerExists(root, ns, LogbackTrafficLogV3Converter.LOGGER_NAME);
    }
}