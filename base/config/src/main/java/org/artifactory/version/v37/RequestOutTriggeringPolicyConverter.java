package org.artifactory.version.v37;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;

/**
 * @author Andrey Tashlitsky
 */
@Slf4j
public class RequestOutTriggeringPolicyConverter implements XmlConverter {

    @Override
    public void convert(Document document) {
        log.info("Changing triggering policy for artifactory-request-out.log");
        Element requestOut = document.getRootElement().getChildren().stream()
                .filter(child -> child.getName().equals("appender"))
                .filter(child -> child.getAttributeValue("name").equals("REQUEST_OUT"))
                .findFirst()
                .orElse(null);
        if (requestOut == null) {
            log.warn("Could not find REQUEST_OUT appender");
            return;
        }
        Element triggeringPolicy = requestOut.getChild("triggeringPolicy");
        if (triggeringPolicy == null) {
            log.warn("Could not find triggering policy");
            return;
        }
        triggeringPolicy.setAttribute("class",
                "org.jfrog.common.logging.logback.triggering.SizeAndIntervalTriggeringPolicy");
        log.info("Changed triggering policy for artifactory-request-out.log");
    }
}
