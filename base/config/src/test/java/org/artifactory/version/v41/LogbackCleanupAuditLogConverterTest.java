package org.artifactory.version.v41;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import static org.artifactory.version.v41.LogbackCleanupAuditLogConverter.APPENDER_NAME;
import static org.artifactory.version.v41.LogbackCleanupAuditLogConverter.LOGGER_NAME;

/**
 * @author morm
 */
class LogbackCleanupAuditLogConverterTest extends LogbackConverterTest {

    @Test
    void testLogbackCleanupAuditConverter() throws Exception {
        Document doc = convertXml("/org/artifactory/logging/version/v41/logback_before_cleanup_audit.xml",
                new LogbackCleanupAuditLogConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        assertAppenderExists(root, ns, APPENDER_NAME);
        assertLoggerExists(root, ns, LOGGER_NAME);
    }
}