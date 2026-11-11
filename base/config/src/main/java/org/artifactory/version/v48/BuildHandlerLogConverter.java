package org.artifactory.version.v48;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;

@Slf4j
public class BuildHandlerLogConverter implements XmlConverter {

    protected static final String BUILD_HANDLER_LOGGER_NAME = "com.jfrog.build";

    private static final String BUILD_HANDLER_LOGGER_CONTENT =
            "<logger name=\"" + BUILD_HANDLER_LOGGER_NAME + "\" level=\"info\"/>";

    @Override
    public void convert(Document doc) {
        log.debug("Starting build-handler log conversion: adding logger for com.jfrog.build");
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        addLogger(root, ns, BUILD_HANDLER_LOGGER_NAME, BUILD_HANDLER_LOGGER_CONTENT);
        log.debug("Completed build-handler log conversion: added logger for com.jfrog.build");
    }
}
