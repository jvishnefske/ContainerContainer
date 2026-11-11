package org.artifactory.version.v43;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;

/**
 * This converter rewrites the log folder for consumption usage logs (artifactory-consumption-usage.log).
 * This is done by changing the file path of the consumption usage log appender.
 * The new path is ${artifactory.usage.dir}/artifactory-consumption-usage.log.
 *
 * @author BarakH
 */
@Slf4j
public class LogbackConsumptionUsageLogFolderConverter implements XmlConverter {

    @Override
    public void convert(Document document) {
        log.info("Starting logback conversion: " +
                         "Rewrite log folder for consumption usage logs (artifactory-consumption-usage.log)");
        Element consumptionUsageLog = document.getRootElement().getChildren().stream()
                                              .filter(child -> child.getName().equals("appender"))
                                              .filter(child -> child.getAttributeValue("name")
                                                                       .equals("CONSUMPTION-USAGE-APPENDER"))
                                              .findFirst()
                                              .orElse(null);
        if (consumptionUsageLog == null) {
            return;
        }
        handleFilePolicyIfNeeded(consumptionUsageLog);
        handleRollingPolicyIfNeeded(consumptionUsageLog);
        handleTriggeringPolicyIfNeeded(consumptionUsageLog);
        log.info("Logback conversion completed: " +
                         "Rewrite log folder for consumption usage logs (artifactory-consumption-usage.log)");
    }

    private void handleFilePolicyIfNeeded(Element consumptionUsageLog) {
        setChildTextIfNeeded(consumptionUsageLog, "File",
                             "${artifactory.usage.dir}/artifactory-consumption-usage.log");
    }

    private void handleRollingPolicyIfNeeded(Element consumptionUsageLog) {
        Element rollingPolicy = consumptionUsageLog.getChild("rollingPolicy");
        if (rollingPolicy != null) {
            setChildTextIfNeeded(rollingPolicy, "FileNamePattern",
                                 "${artifactory.usage.dir.archived}/artifactory-consumption-usage.%i.log.gz");
        }
    }

    private void handleTriggeringPolicyIfNeeded(Element consumptionUsageLog) {
        Element triggeringPolicy = consumptionUsageLog.getChild("triggeringPolicy");
        if (triggeringPolicy != null) {
            setChildTextIfNeeded(triggeringPolicy, "MaxFileSize", "100MB");
        }
    }

    private void setChildTextIfNeeded(Element element, String childName, String text) {
        Element maxFileSize = element.getChild(childName);
        if (maxFileSize != null) {
            maxFileSize.setText(text);
        }
    }
}
