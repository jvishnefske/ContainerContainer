package org.artifactory.version.v42;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;

/**
 * This converter removes the compression for artifactory-traffic-v2.log.
 * Also: reducing the file size to 100MB, increasing the number of files to 500 and removing compressLatest.
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
public class TrafficLogDisableCompressionConverter implements XmlConverter {

    @Override
    public void convert(Document document) {
        log.info("Starting logback conversion: Remove compression of traffic log v2");
        Element trafficLogV2 = document.getRootElement().getChildren().stream()
                .filter(child -> child.getName().equals("appender"))
                .filter(child -> child.getAttributeValue("name").equals("TRAFFIC_V2"))
                .findFirst()
                .orElse(null);
        if (trafficLogV2 == null) {
            return;
        }
        handleRollingPolicyIfNeeded(trafficLogV2);
        handleTriggeringPolicyIfNeeded(trafficLogV2);
        log.info("Logback conversion completed: compression removed from traffic log v2");
    }

    private void handleRollingPolicyIfNeeded(Element trafficLogV2) {
        Element rollingPolicy = trafficLogV2.getChild("rollingPolicy");
        if (rollingPolicy != null) {
            setChildTextIfNeeded(rollingPolicy, "maxIndex", "500");
            handleFileNamePatternIfNeeded(rollingPolicy);
            rollingPolicy.removeChild("compressLatest");
        }
    }

    private void setChildTextIfNeeded(Element element, String childName, String text) {
        Element maxFileSize = element.getChild(childName);
        if (maxFileSize != null) {
            maxFileSize.setText(text);
        }
    }

    private void handleFileNamePatternIfNeeded(Element rollingPolicy) {
        Element fileNamePattern = rollingPolicy.getChild("FileNamePattern");
        if (fileNamePattern != null) {
            fileNamePattern.setText(StringUtils.removeEnd(fileNamePattern.getText(), ".gz"));
        }
    }

    private void handleTriggeringPolicyIfNeeded(Element trafficLogV2) {
        Element triggeringPolicy = trafficLogV2.getChild("triggeringPolicy");
        if (triggeringPolicy != null) {
            setChildTextIfNeeded(triggeringPolicy, "MaxFileSize", "100MB");
        }
    }
}
