package org.artifactory.version.v45;

import jakarta.annotation.Nullable;
import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.jupiter.api.Test;

import static org.artifactory.version.v45.LogbackStorageOutboundRequestLogConverter.APPENDER_NAME;
import static org.artifactory.version.v45.LogbackStorageOutboundRequestLogConverter.CONSOLE_APPENDER_NAME;
import static org.artifactory.version.v45.LogbackStorageOutboundRequestLogConverter.LOGGER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Idan Dadon
 */
public class LogbackStorageOutboundRequestLogConverterTest extends LogbackConverterTest {

    @Test
    void testConvertWhenWrongAppenderExist() throws Exception {
        Document document = convertXml("/org/artifactory/logging/version/v45/logback_with_wrong_data.xml",
                new LogbackStorageOutboundRequestLogConverter());
        Element requestMdcAppender = getStorageRequestOutAppender(document);
        Element requestMdcConsoleAppender = getStorageRequestOutConsoleAppender(document);
        Element requestMdcLogger = getRemoteRequestOutLogger(document);
        assertConsumptionAppenderAndLoggerIsValid(requestMdcAppender, requestMdcConsoleAppender, requestMdcLogger);
    }

    @Test
    void testConvertWhenCorrectAppenderExist() throws Exception {
        Document document = convertXml("/org/artifactory/logging/version/v45/logback_with_updated_data.xml",
                new LogbackStorageOutboundRequestLogConverter());
        Element requestMdcAppender = getStorageRequestOutAppender(document);
        Element requestMdcConsoleAppender = getStorageRequestOutConsoleAppender(document);
        Element requestMdcLogger = getRemoteRequestOutLogger(document);
        assertConsumptionAppenderAndLoggerIsValid(requestMdcAppender, requestMdcConsoleAppender, requestMdcLogger);
    }

    @Nullable
    private static Element getStorageRequestOutAppender(Document document) {
        return document.getRootElement().getChildren().stream()
                .filter(child -> child.getName().equals("appender"))
                .filter(child -> child.getAttributeValue("name")
                        .equals(APPENDER_NAME))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private static Element getStorageRequestOutConsoleAppender(Document document) {
        return document.getRootElement().getChildren().stream()
                .filter(child -> child.getName().equals("appender"))
                .filter(child -> child.getAttributeValue("name")
                        .equals(CONSOLE_APPENDER_NAME))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private static Element getRemoteRequestOutLogger(Document document) {
        return document.getRootElement().getChildren().stream()
                .filter(child -> child.getName().equals("logger"))
                .filter(child -> child.getAttributeValue("name")
                        .equals(LOGGER_NAME))
                .findFirst()
                .orElse(null);
    }

    private static void assertConsumptionAppenderAndLoggerIsValid(Element requestMdcAppender,
            Element requestMdcConsoleAppender, Element requestMdcLogger) {
        assertNotNull(requestMdcAppender);
        assertNotNull(requestMdcConsoleAppender);
        assertNotNull(requestMdcLogger);
        assertTrue(requestMdcAppender.getChild("encoder").getChild("pattern").getText().contains("%mdc"));
        assertTrue(requestMdcConsoleAppender.getAttribute("name").getValue().equals("STORAGE_REQUEST_OUT_STDOUT"));
        Element triggeringPolicy = requestMdcAppender.getChild("triggeringPolicy");
        assertEquals("25MB", triggeringPolicy.getChild("MaxFileSize").getText());
    }
}
