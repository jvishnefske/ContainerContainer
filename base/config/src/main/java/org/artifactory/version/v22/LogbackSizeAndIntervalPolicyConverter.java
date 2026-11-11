package org.artifactory.version.v22;

import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class LogbackSizeAndIntervalPolicyConverter implements XmlConverter {

    private static final Logger log = LoggerFactory.getLogger(LogbackSizeAndIntervalPolicyConverter.class);

    static final Set<String> APPENDER_NAMES = Set.of("FILE", "REQUEST", "ACCESS");
    static final String POLICY_CLASS =
            "org.jfrog.common.logging.logback.triggering.SizeAndIntervalTriggeringPolicy";

    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion --> Changing triggering policy for {}.", APPENDER_NAMES);
        doc.getRootElement()
                .getChildren()
                .stream()
                .filter(this::isListedAppender)
                .forEach(this::replacePolicyClass);
        log.debug("Changing triggering policy for {} completed.", APPENDER_NAMES);
    }

    private void replacePolicyClass(Element appender) {
        Element triggeringPolicy = appender.getChild("triggeringPolicy");
        if (triggeringPolicy == null) {
            String name = appender.getAttribute("name").getValue();
            log.warn("Appender {} does not contain triggeringPolicy", name);
        } else {
            triggeringPolicy.setAttribute("class", POLICY_CLASS);
        }
    }

    private boolean isListedAppender(Element element) {
        return element.getName().equals("appender") && APPENDER_NAMES.contains(element.getAttribute("name").getValue());
    }

}
