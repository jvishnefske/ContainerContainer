package org.artifactory.version.v23;

import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.artifactory.logging.converter.LogbackConverterUtils.removeLoggerIfExists;

public class LogbackRemoveStartArtifactoryProWithServicesLogger implements XmlConverter {

    private static final Logger log = LoggerFactory.getLogger(LogbackRemoveStartArtifactoryProWithServicesLogger.class);

    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion --> Removing StartArtifactoryProWithServices logger.");

        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        removeLoggerIfExists(root, ns, "org.artifactory.webapp.main.StartArtifactoryProWithServices");

        log.debug("Logback conversion --> Remove StartArtifactoryProWithServices completed.");
    }
}
