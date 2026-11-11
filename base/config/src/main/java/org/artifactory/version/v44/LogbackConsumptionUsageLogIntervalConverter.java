package org.artifactory.version.v44;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;

/**
 * This converter rewrites the log rotation interval for consumption usage logs (artifactory-consumption-usage.log).
 *
 * @author BarakH
 */
@Slf4j
public class LogbackConsumptionUsageLogIntervalConverter implements XmlConverter {

    @Override
    public void convert(Document document) {
        log.info(
                "Starting logback conversion: Rewrite log rotation interval for consumption usage logs " +
                        "(artifactory-consumption-usage.log)");
        Element consumptionUsageLog = document.getRootElement().getChildren().stream()
                                              .filter(child -> child.getName().equals("appender"))
                                              .filter(child -> child.getAttributeValue("name")
                                                                       .equals("CONSUMPTION-USAGE-APPENDER"))
                                              .findFirst()
                                              .orElse(null);
        if (consumptionUsageLog == null) {
            log.error("Did not find consumption usage log appender in logback configuration. Skipping conversion.");
            return;
        }
        handleTriggeringPolicyIfNeeded(consumptionUsageLog);
        log.info(
                "Logback conversion completed: Rewrite log rotation interval for consumption usage logs " +
                        "(artifactory-consumption-usage.log)");
    }

    private void handleTriggeringPolicyIfNeeded(Element consumptionUsageLog) {
        Element triggeringPolicy = consumptionUsageLog.getChild("triggeringPolicy");
        if (triggeringPolicy != null) {
            triggeringPolicy.setAttribute(
                    "class", "org.jfrog.common.logging.logback.triggering.SizeAndIntervalTriggeringPolicy");
            createOrSetChildText(triggeringPolicy, "MaxFileSize", "25MB");
            createOrSetChildText(triggeringPolicy, "intervalMs", "900000");
        }
    }

    private void createOrSetChildText(Element element, String childName, String text) {
        Element maxFileSize = element.getChild(childName);
        if (maxFileSize != null) {
            maxFileSize.setText(text);
        } else {
            Element newElement = new Element(childName);
            newElement.setText(text);
            element.addContent(newElement);
        }
    }
}
