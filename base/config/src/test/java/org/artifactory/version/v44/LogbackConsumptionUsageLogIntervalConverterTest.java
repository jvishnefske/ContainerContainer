package org.artifactory.version.v44;

import jakarta.annotation.Nullable;
import org.artifactory.convert.LogbackConverterTest;
import org.artifactory.version.v33.LogbackConsumptionUsageLogConverter;
import org.artifactory.version.v43.LogbackConsumptionUsageLogFolderConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author BarakH
 */
class LogbackConsumptionUsageLogIntervalConverterTest extends LogbackConverterTest {

    @Test
    void testConvertWhenNoAppender() throws Exception {
        Document document = convertXml("/org/artifactory/logging/version/v43/logback_with_no_appender.xml",
                                       new LogbackConsumptionUsageLogConverter(),
                                       new LogbackConsumptionUsageLogFolderConverter(),
                                       new LogbackConsumptionUsageLogIntervalConverter());
        Element consumptionLog = gerConsumptionUsageAppender(document);
        assertConsumptionAppenderIsValid(consumptionLog);
    }

    @Test
    void testConvertWhenWrongAppenderExist() throws Exception {
        Document document = convertXml("/org/artifactory/logging/version/v44/logback_with_wrong_data.xml",
                                       new LogbackConsumptionUsageLogIntervalConverter());
        Element consumptionLog = gerConsumptionUsageAppender(document);
        assertConsumptionAppenderIsValid(consumptionLog);
    }

    @Test
    void testConvertWhenGoodAppenderExist() throws Exception {
        Document document = convertXml("/org/artifactory/logging/version/v44/logback_with_good_data.xml",
                                       new LogbackConsumptionUsageLogIntervalConverter());
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
        Element triggeringPolicy = consumptionLog.getChild("triggeringPolicy");
        assertEquals("org.jfrog.common.logging.logback.triggering.SizeAndIntervalTriggeringPolicy",
                     triggeringPolicy.getAttributeValue("class"));
        assertEquals("25MB", triggeringPolicy.getChild("MaxFileSize").getText());
        assertEquals("900000", triggeringPolicy.getChild("intervalMs").getText());
    }

}