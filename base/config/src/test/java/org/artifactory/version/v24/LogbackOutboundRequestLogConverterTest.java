package org.artifactory.version.v24;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import static org.artifactory.version.v24.LogbackOutboundRequestLogConverter.APPENDER_NAME;
import static org.artifactory.version.v24.LogbackOutboundRequestLogConverter.LOGGER_NAME;

/**
 * @author Andrey Tashlitsky
 */
class LogbackOutboundRequestLogConverterTest extends LogbackConverterTest {

    @Test
    void testOutboundRequestLogAppendersAndLoggers() throws Exception {
        Document doc = convertXml("/org/artifactory/logging/version/v24/before_outbound_request_log.xml",
                new LogbackOutboundRequestLogConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        assertAppenderExists(root, ns, APPENDER_NAME);
        assertLoggerExists(root, ns, LOGGER_NAME);
    }
}
