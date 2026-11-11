package org.artifactory.version.v34;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;

@Slf4j
public class BundleHandlerLogConverter implements XmlConverter {

    protected static final String BUNDLE_HANDLER_LOGGER_NAME = "com.jfrog.bh";
    protected static final String BUNDLE_HANDLER_CRYPTO_LOGGER_NAME = "com.jfrog.cs";

    private static final String BUNDLE_HANDLER_CRYPTO_LOGGER_CONTENT =
            "<logger name=\"" + BUNDLE_HANDLER_CRYPTO_LOGGER_NAME + "\" level=\"info\"/>";
    private static final String BUNDLE_HANDLER_LOGGER_CONTENT =
            "<logger name=\"" + BUNDLE_HANDLER_LOGGER_NAME + "\" level=\"info\"/>";

    @Override
    public void convert(Document doc) {
        log.debug("Starting bundle handler log conversion: adding logger for com.jfrog.bh and com.jfrog.cs");
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        addLogger(root, ns, BUNDLE_HANDLER_LOGGER_NAME, BUNDLE_HANDLER_LOGGER_CONTENT);
        addLogger(root, ns, BUNDLE_HANDLER_CRYPTO_LOGGER_NAME, BUNDLE_HANDLER_CRYPTO_LOGGER_CONTENT);
        log.debug("Completed log conversion for bundle handler: added logger for com.jfrog.bh and com.jfrog.cs");
    }
}