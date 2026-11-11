package org.artifactory.version.v27;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import java.util.stream.Collectors;

/**
 * This converter changes the interval to 21600000 for USAGE-METRICS-APPENDER.
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
public class LogbackFeatureAdoptionAppenderIntervalConverter implements XmlConverter {

    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion: changing the interval to 21600000 for USAGE-METRICS-APPENDER.");
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        Element usageMetricsAppender = root.getChildren("appender", ns).stream()
                .filter(element -> element.getAttribute("name").getValue().equals("USAGE-METRICS-APPENDER"))
                .collect(Collectors.toList()).get(0);
        Element intervalMs = usageMetricsAppender.getChild("intervalMs");
        intervalMs.setText("21600000");
        log.debug("Logback conversion completed: the interval for USAGE-METRICS-APPENDER was changed successfully.");
    }
}
