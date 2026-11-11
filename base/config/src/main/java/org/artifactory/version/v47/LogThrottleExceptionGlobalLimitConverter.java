package org.artifactory.version.v47;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.logging.converter.LogbackConverterUtils.updateProperty;

@Slf4j
public class LogThrottleExceptionGlobalLimitConverter implements XmlConverter {

    static final String THROTTLE_EXCEPTION_GLOBAL_LIMIT = "throttle-exceptions-global-limit";
    static final String THROTTLE_EXCEPTION_GLOBAL_LIMIT_VALUE = "${log.exceptions.throttle.global.limit:--1}";

    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion: update log throttling exception global limit property.");

        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();

        updateProperty(root, ns, THROTTLE_EXCEPTION_GLOBAL_LIMIT, THROTTLE_EXCEPTION_GLOBAL_LIMIT_VALUE);

        log.debug("Logback conversion completed: log throttling exception global limit property updated successfully.");
    }
}
