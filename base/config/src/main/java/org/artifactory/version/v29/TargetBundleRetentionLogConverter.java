package org.artifactory.version.v29;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;

@Slf4j
public class TargetBundleRetentionLogConverter implements XmlConverter {

    public static final String LOGGER_NAME = "com.jfrog.retention";
    private static final String LOGGER_CONTENT = "<logger name=\"" + LOGGER_NAME + "\" level=\"info\"/>";

    @Override
    public void convert(Document doc) {
        log.debug("Starting target bundle retention log conversion: adding logger for com.jfrog.retention");
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        addLogger(root, ns, LOGGER_NAME, LOGGER_CONTENT);
        log.debug("Completed log conversion for target bundle retention: added logger for com.jfrog.retention");
    }
}
