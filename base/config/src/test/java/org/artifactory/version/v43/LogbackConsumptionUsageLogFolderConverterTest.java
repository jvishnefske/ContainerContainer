package org.artifactory.version.v43;

import jakarta.annotation.Nullable;
import org.artifactory.convert.LogbackConverterTest;
import org.artifactory.version.v33.LogbackConsumptionUsageLogConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author BarakH
 */
class LogbackConsumptionUsageLogFolderConverterTest extends LogbackConverterTest {

    @Test
    void testConvertWhenNoAppender() throws Exception {
        Document document = convertXml("/org/artifactory/logging/version/v43/logback_with_no_appender.xml",
                                       new LogbackConsumptionUsageLogConverter(),
                                       new LogbackConsumptionUsageLogFolderConverter());
        Element consumptionLog = gerConsumptionUsageAppender(document);
        assertConsumptionAppenderIsValid(consumptionLog);
    }

    @Test
    void testConvertWhenWrongAppenderExist() throws Exception {
        Document document = convertXml("/org/artifactory/logging/version/v43/logback_with_wrong_data.xml",
                                       new LogbackConsumptionUsageLogFolderConverter());
        Element consumptionLog = gerConsumptionUsageAppender(document);
        assertConsumptionAppenderIsValid(consumptionLog);
    }

    @Test
    void testConvertWhenGoodAppenderExist() throws Exception {
        Document document = convertXml("/org/artifactory/logging/version/v43/logback_with_good_data.xml",
                                       new LogbackConsumptionUsageLogFolderConverter());
        Element consumptionLog = gerConsumptionUsageAppender(document);
        assertConsumptionAppenderIsValid(consumptionLog);
    }

    @Nullable
    private static Element gerConsumptionUsageAppender(Document document) {
        return document.getRootElement().getChildren().stream()
                       .filter(child -> child.getName().equals("appender"))
                       .filter(child -> child.getAttributeValue("name")
                                                .equals("CONSUMPTION-USAGE-APPENDER"))
                       .findFirst()
                       .orElse(null);
    }

    private static void assertConsumptionAppenderIsValid(Element consumptionLog) {
        assertNotNull(consumptionLog);

        Element file = consumptionLog.getChild("File");
        assertEquals("${artifactory.usage.dir}/artifactory-consumption-usage.log", file.getText());

        Element fileNamePattern = consumptionLog.getChild("rollingPolicy").getChild("FileNamePattern");
        assertEquals("${artifactory.usage.dir.archived}/artifactory-consumption-usage.%i.log.gz",
                     fileNamePattern.getText());

        Element maxFileSize = consumptionLog.getChild("triggeringPolicy").getChild("MaxFileSize");
        assertEquals("100MB", maxFileSize.getText());
    }

}