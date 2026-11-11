package org.artifactory.version.v37;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Andrey Tashlitsky
 */
class RequestOutTriggeringPolicyConverterTest extends LogbackConverterTest {

    @Test
    void testConvert() throws Exception {
        Document document = convertXml("/org/artifactory/logging/version/v37/logback_old_triggering_policy.xml",
                new RequestOutTriggeringPolicyConverter());
        Element requestOut = document.getRootElement().getChildren().stream()
                .filter(child -> child.getName().equals("appender"))
                .filter(child -> child.getAttributeValue("name").equals("REQUEST_OUT"))
                .findFirst()
                .orElse(null);
        assertNotNull(requestOut);
        Element triggeringPolicy = requestOut.getChild("triggeringPolicy");
        Attribute classAttribute = triggeringPolicy.getAttribute("class");
        assertEquals("org.jfrog.common.logging.logback.triggering.SizeAndIntervalTriggeringPolicy",
                classAttribute.getValue());
    }

    @Test
    void testConvertNoTriggeringPolicy() throws Exception {
        Document document = convertXml("/org/artifactory/logging/version/v37/logback_no_triggering_policy.xml",
                new RequestOutTriggeringPolicyConverter());
        Element requestOut = document.getRootElement().getChildren().stream()
                .filter(child -> child.getName().equals("appender"))
                .filter(child -> child.getAttributeValue("name").equals("REQUEST_OUT"))
                .findFirst()
                .orElse(null);
        assertNotNull(requestOut);
        Element triggeringPolicy = requestOut.getChild("triggeringPolicy");
        assertNull(triggeringPolicy);
    }

    @Test
    void testConvertNoAppender() throws Exception {
        Document document = convertXml("/org/artifactory/logging/version/v37/logback_no_appender.xml",
                new RequestOutTriggeringPolicyConverter());
        Element requestOut = document.getRootElement().getChildren().stream()
                .filter(child -> child.getName().equals("appender"))
                .filter(child -> child.getAttributeValue("name").equals("REQUEST_OUT"))
                .findFirst()
                .orElse(null);
        assertNull(requestOut);
    }
}
