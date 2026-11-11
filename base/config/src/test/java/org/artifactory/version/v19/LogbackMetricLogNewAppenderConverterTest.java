package org.artifactory.version.v19;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.artifactory.logging.converter.LogbackConverterUtils.ATTRIBUTE_NAME;
import static org.artifactory.logging.converter.LogbackConverterUtils.ELEMENT_TYPE_APPENDER;
import static org.artifactory.version.v19.LogbackMetricLogNewAppenderConverter.APPENDER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LogbackMetricLogNewAppenderConverterTest extends LogbackConverterTest {

    @Test
    void test() throws Exception {
        Document doc = convertXml("/org/artifactory/logging/version/v19/before.xml",
                new LogbackMetricLogNewAppenderConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();

        assertAppenderExists(root, ns, APPENDER_NAME);
        List<Element> appenderElements = root.getChildren(ELEMENT_TYPE_APPENDER, ns)
                .stream()
                .filter(element -> element.getAttributeValue(ATTRIBUTE_NAME, ns).equals(APPENDER_NAME)
                        && element.getAttributeValue("class", ns).contains("MetricTimeBasedRollingFileAppender"))
                .collect(Collectors.toList());

        assertEquals(1, appenderElements.size());
    }

}
