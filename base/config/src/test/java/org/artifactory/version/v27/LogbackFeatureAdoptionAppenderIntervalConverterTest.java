package org.artifactory.version.v27;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Andrey Tashlitsky
 */
class LogbackFeatureAdoptionAppenderIntervalConverterTest extends LogbackConverterTest {

    @Test
    void testFeatureAdoptionAppenderInterval() throws Exception {
        Document doc = convertXml("/org/artifactory/logging/version/v27/feature_adoption_old_interval.xml",
                new LogbackFeatureAdoptionAppenderIntervalConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        Element usageMetricsAppender = root.getChildren("appender", ns).stream()
                .filter(element -> element.getAttribute("name").getValue().equals("USAGE-METRICS-APPENDER"))
                .collect(Collectors.toList()).get(0);
        Element intervalMs = usageMetricsAppender.getChild("intervalMs");
        assertEquals("21600000", intervalMs.getText());
    }
}
