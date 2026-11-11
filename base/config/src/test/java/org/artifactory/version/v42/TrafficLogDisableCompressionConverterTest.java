package org.artifactory.version.v42;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Andrey Tashlitsky
 */
class TrafficLogDisableCompressionConverterTest extends LogbackConverterTest {

    @Test
    void testConvert() throws Exception {
        Document document = convertXml("/org/artifactory/logging/version/v42/logback_appender_with_compression.xml",
                new TrafficLogDisableCompressionConverter());
        Element trafficLogV2 = document.getRootElement().getChildren().stream()
                .filter(child -> child.getName().equals("appender"))
                .filter(child -> child.getAttributeValue("name").equals("TRAFFIC_V2"))
                .findFirst()
                .orElse(null);
        assertNotNull(trafficLogV2);
        Element maxIndex = trafficLogV2.getChild("rollingPolicy").getChild("maxIndex");
        assertEquals("500", maxIndex.getText());
        Element fileNamePattern = trafficLogV2.getChild("rollingPolicy").getChild("FileNamePattern");
        assertFalse(fileNamePattern.getText().endsWith(".gz"));
        Element compressLatest = trafficLogV2.getChild("rollingPolicy").getChild("compressLatest");
        assertNull(compressLatest);
        Element maxFileSize = trafficLogV2.getChild("triggeringPolicy").getChild("MaxFileSize");
        assertEquals("100MB", maxFileSize.getText());
    }

    @Test
    void testConvertNoAppender() throws Exception {
        Document document = convertXml("/org/artifactory/logging/version/v42/logback_no_appender.xml",
                new TrafficLogDisableCompressionConverter());
        Element trafficLogV2 = document.getRootElement().getChildren().stream()
                .filter(child -> child.getName().equals("appender"))
                .filter(child -> child.getAttributeValue("name").equals("TRAFFIC_V2"))
                .findFirst()
                .orElse(null);
        assertNull(trafficLogV2);
    }

    @Test
    void testConvertNoPolicies() throws Exception {
        Document document = convertXml("/org/artifactory/logging/version/v42/logback_appender_no_policies.xml",
                new TrafficLogDisableCompressionConverter());
        Element trafficLogV2 = document.getRootElement().getChildren().stream()
                .filter(child -> child.getName().equals("appender"))
                .filter(child -> child.getAttributeValue("name").equals("TRAFFIC_V2"))
                .findFirst()
                .orElse(null);
        assertNotNull(trafficLogV2);
        Element rollingPolicy = trafficLogV2.getChild("rollingPolicy");
        assertNull(rollingPolicy);
        Element triggeringPolicy = trafficLogV2.getChild("triggeringPolicy");
        assertNull(triggeringPolicy);
    }

    @Test
    void testConvertNoElements() throws Exception {
        Document document = convertXml("/org/artifactory/logging/version/v42/logback_appender_no_elements.xml",
                new TrafficLogDisableCompressionConverter());
        Element trafficLogV2 = document.getRootElement().getChildren().stream()
                .filter(child -> child.getName().equals("appender"))
                .filter(child -> child.getAttributeValue("name").equals("TRAFFIC_V2"))
                .findFirst()
                .orElse(null);
        assertNotNull(trafficLogV2);
        Element maxIndex = trafficLogV2.getChild("rollingPolicy").getChild("maxIndex");
        assertNull(maxIndex);
        Element fileNamePattern = trafficLogV2.getChild("rollingPolicy").getChild("FileNamePattern");
        assertNull(fileNamePattern);
        Element compressLatest = trafficLogV2.getChild("rollingPolicy").getChild("compressLatest");
        assertNull(compressLatest);
        Element maxFileSize = trafficLogV2.getChild("triggeringPolicy").getChild("MaxFileSize");
        assertNull(maxFileSize);
    }
}
