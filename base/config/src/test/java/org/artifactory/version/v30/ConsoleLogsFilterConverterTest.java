package org.artifactory.version.v30;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.artifactory.version.v30.ConsoleLogsFilterConverter.ACCESS_APPENDER;
import static org.artifactory.version.v30.ConsoleLogsFilterConverter.CONSOLE_APPENDER;
import static org.artifactory.version.v30.ConsoleLogsFilterConverter.REQUEST_APPENDER;
import static org.artifactory.version.v30.ConsoleLogsFilterConverter.REQUEST_OUT_APPENDER;
import static org.artifactory.version.v30.ConsoleLogsFilterConverter.SERVICE_APPENDER;

/**
 * @author Dor Solomon
 */
public class ConsoleLogsFilterConverterTest extends LogbackConverterTest {

    @Test
    public void testConsoleLogFilterConversion() throws Exception {
        Document doc = convertXml("/org/artifactory/logging/version/v30/before_console_logs_filter.xml",
                new ConsoleLogsFilterConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        assertFilterExists(root, ns, CONSOLE_APPENDER, "org.artifactory.logging.filters.ConsoleStandardFormatFilter");
        List.of(ACCESS_APPENDER, REQUEST_APPENDER, REQUEST_APPENDER, SERVICE_APPENDER, REQUEST_OUT_APPENDER)
                .forEach(appender -> {
                    assertFilterExists(root, ns, appender, "org.artifactory.logging.filters.ConsoleJsonFormatFilter");
                    assertFilterDoesNotExist(root, ns, appender, "org.artifactory.logging.filters.ConsoleFilter");
                });
    }
}