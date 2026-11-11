package org.artifactory.version.v38;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.jupiter.api.Test;

import static org.artifactory.version.v38.FederationLogAppenderConverter.APPENDER_NAME;
import static org.artifactory.version.v38.FederationLogAppenderConverter.FEDERATION_LOGGER_PACKAGE;
import static org.artifactory.version.v38.FederationLogAppenderConverter.MIRROR_LOGGER_PACKAGE;
import static org.artifactory.version.v38.FederationLogAppenderConverter.STDOUT_APPENDER_NAME;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Alexei Vainshtein
 */
class FederationLogAppenderConverterTest extends LogbackConverterTest {

    @Test
    void testConvert() throws Exception {
        Document document = convertXml("/org/artifactory/logging/version/v38/logback_no_appender.xml",
                new FederationLogAppenderConverter());
        Element federationAppender = document.getRootElement().getChildren().stream()
                .filter(child -> child.getName().equals("appender"))
                .filter(child -> child.getAttributeValue("name").equals(APPENDER_NAME))
                .findFirst()
                .orElse(null);
        Element stdoutFederationAppender = document.getRootElement().getChildren().stream()
                .filter(child -> child.getName().equals("appender"))
                .filter(child -> child.getAttributeValue("name").equals(STDOUT_APPENDER_NAME))
                .findFirst()
                .orElse(null);
        Element federationPackageLogger = document.getRootElement().getChildren().stream()
                .filter(child -> child.getName().equals("logger"))
                .filter(child -> child.getAttributeValue("name").equals(FEDERATION_LOGGER_PACKAGE))
                .findFirst()
                .orElse(null);
        Element mirrorPackageLogger = document.getRootElement().getChildren().stream()
                .filter(child -> child.getName().equals("logger"))
                .filter(child -> child.getAttributeValue("name").equals(MIRROR_LOGGER_PACKAGE))
                .findFirst()
                .orElse(null);
        assertNotNull(federationAppender);
        assertNotNull(stdoutFederationAppender);
        assertNotNull(federationPackageLogger);
        assertNotNull(mirrorPackageLogger);
    }
}