package org.artifactory.version.v20;

import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.artifactory.logging.converter.LogbackConverterUtils.addAppender;
import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;
import static org.artifactory.logging.converter.LogbackConverterUtils.removeAppenderIfExists;
import static org.artifactory.logging.converter.LogbackConverterUtils.removeLoggerIfExists;

public class LogbackMetricLogAppenderOrderConverter implements XmlConverter {

    private static final Logger log = LoggerFactory.getLogger(LogbackMetricLogAppenderOrderConverter.class);

    protected static final String APPENDER_NAME = "METRICS-APPENDER";
    protected static final String LOGGER_NAME = "METRICS";

    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion --> Fixing appender logger order for metric log.");

        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        Optional<Element> appender = removeAppenderIfExists(root, ns, APPENDER_NAME);
        Optional<Element> logger = removeLoggerIfExists(root, ns, LOGGER_NAME);
        appender.ifPresent(element -> addAppender(root, ns, APPENDER_NAME, element));
        logger.ifPresent(element -> addLogger(root, ns, LOGGER_NAME, element));

        log.debug("Metric appender logger order conversion completed.");
    }
}
