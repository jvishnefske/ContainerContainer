package org.artifactory.version.v40;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.artifactory.version.v29.TargetBundleRetentionLogConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;
import static org.artifactory.logging.converter.LogbackConverterUtils.removeLoggerIfExists;

/**
 * @author Hezi Cohen
 * 25/02/2024
 */
@Slf4j
public class DistributionHandlerLogConverter implements XmlConverter {
    protected static final String LOGGER_NAME = "com.jfrog.distribution.handler";
    private static final String LOGGER_CONTENT = "<logger name=\"" + LOGGER_NAME + "\" level=\"info\"/>";

    @Override
    public void convert(Document doc) {
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();

        log.info("Starting distribution-handler log conversion: removing old logger {} if exists",
                TargetBundleRetentionLogConverter.LOGGER_NAME);
        removeLoggerIfExists(root, ns, TargetBundleRetentionLogConverter.LOGGER_NAME);

        log.info("distribution-handler log conversion: adding logger for com.jfrog.distribution.handler");
        addLogger(root, ns, LOGGER_NAME, LOGGER_CONTENT);

        log.info("Completed log conversion for distribution-handler: added logger for com.jfrog.distribution.handler");
    }
}
