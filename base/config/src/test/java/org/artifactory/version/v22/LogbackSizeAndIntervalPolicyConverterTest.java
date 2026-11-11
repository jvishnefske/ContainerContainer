package org.artifactory.version.v22;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.testng.annotations.Test;

import static org.artifactory.version.v22.LogbackSizeAndIntervalPolicyConverter.APPENDER_NAMES;
import static org.artifactory.version.v22.LogbackSizeAndIntervalPolicyConverter.POLICY_CLASS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

public class LogbackSizeAndIntervalPolicyConverterTest extends LogbackConverterTest {

    @Test
    public void test() throws Exception {
        Document doc = convertXml("/org/artifactory/logging/version/v22/before_size_and_interval_policy.xml",
                new LogbackSizeAndIntervalPolicyConverter());

        doc.getRootElement().getChildren().stream()
                .filter(e -> e.getName().equals("appender"))
                .forEach(e -> assertPolicyClass(e));
    }

    private void assertPolicyClass(Element e) {
        String name = e.getAttributeValue("name");
        boolean converted = APPENDER_NAMES.contains(name);
        Element triggeringPolicy = e.getChild("triggeringPolicy");
        if (converted) {
            String className = triggeringPolicy.getAttributeValue("class");
            assertEquals(className, POLICY_CLASS);
        } else {
            if (triggeringPolicy != null) {
                String className = triggeringPolicy.getAttributeValue("class");
                assertNotEquals(className, POLICY_CLASS);
            }
        }

    }
}

