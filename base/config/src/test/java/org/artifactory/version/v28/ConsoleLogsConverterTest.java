package org.artifactory.version.v28;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import static org.artifactory.version.v28.ConsoleLogsConverter.ACCESS_APPENDER;
import static org.artifactory.version.v28.ConsoleLogsConverter.ACCESS_LOGGER_NAME;
import static org.artifactory.version.v28.ConsoleLogsConverter.REQUEST_APPENDER;
import static org.artifactory.version.v28.ConsoleLogsConverter.REQUEST_LOGGER_NAME;
import static org.artifactory.version.v28.ConsoleLogsConverter.REQUEST_OUT_APPENDER;
import static org.artifactory.version.v28.ConsoleLogsConverter.REQUEST_OUT_LOGGER_NAME;
import static org.artifactory.version.v28.ConsoleLogsConverter.SERVICE_APPENDER;

/**
 * @author Tomer Slutsky
 */
public class ConsoleLogsConverterTest extends LogbackConverterTest {

    @Test
    public void testConsoleLogAppendersAndLoggers() throws Exception {
        Document doc = convertXml("/org/artifactory/logging/version/v28/before_console_logs.xml",
                new ConsoleLogsConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        assertAppenderExists(root, ns, ACCESS_APPENDER);
        assertAppenderExists(root, ns, REQUEST_APPENDER);
        assertAppenderExists(root, ns, SERVICE_APPENDER);
        assertAppenderExists(root, ns, REQUEST_OUT_APPENDER);
        assertLayoutExists(root, ns, ACCESS_APPENDER,
                "org.artifactory.logging.layout.access.AccessJsonLayout");
        assertLayoutExists(root, ns, SERVICE_APPENDER, "org.artifactory.logging.layout.service.ServiceJsonLayout");
        assertLayoutExists(root, ns, REQUEST_APPENDER, "org.artifactory.logging.layout.request.RequestJsonLayout");
        assertLayoutExists(root, ns, REQUEST_OUT_APPENDER,
                "org.artifactory.logging.layout.request.OutboundRequestJsonLayout");
        assertLoggerExists(root, ns, ACCESS_LOGGER_NAME);
        assertLoggerExists(root, ns, REQUEST_LOGGER_NAME);
        assertLoggerExists(root, ns, REQUEST_OUT_LOGGER_NAME);
        assertAppenderRefExists(root, ns, ACCESS_LOGGER_NAME, ACCESS_APPENDER);
        assertAppenderRefExists(root, ns, REQUEST_LOGGER_NAME, REQUEST_APPENDER);
        assertAppenderRefExists(root, ns, REQUEST_OUT_LOGGER_NAME, REQUEST_OUT_APPENDER);
    }
}
