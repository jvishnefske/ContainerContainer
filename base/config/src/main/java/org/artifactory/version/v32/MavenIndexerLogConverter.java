package org.artifactory.version.v32;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;

/**
 * Reduce the warning level of org.apache.maven.index.ArtifactContext, to prevent log pollution
 */
@Slf4j
public class MavenIndexerLogConverter implements XmlConverter {

    protected static final String LOGGER_NAME = "org.apache.maven.index.ArtifactContext";
    private static final String LOGGER_CONTENT = "<logger name=\"" + LOGGER_NAME + "\" level=\"ERROR\"/>";

    @Override
    public void convert(Document doc) {
        log.debug("Starting Maven Indexer log conversion: adding logger for {}", LOGGER_NAME);
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        addLogger(root, ns, LOGGER_NAME, LOGGER_CONTENT);
        log.debug("Completed log conversion for Maven Indexer: added logger for {}", LOGGER_NAME);
    }
}
