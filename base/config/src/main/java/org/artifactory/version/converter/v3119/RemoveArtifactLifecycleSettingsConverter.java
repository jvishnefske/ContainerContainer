package org.artifactory.version.converter.v3119;

import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Removes the old artifactLifecycleSettings element from the config descriptor.
 *
 * @author Tamar Rosen
 */
public class RemoveArtifactLifecycleSettingsConverter implements XmlConverter {
    private static final Logger log = LoggerFactory.getLogger(RemoveArtifactLifecycleSettingsConverter.class);

    @Override
    public void convert(Document doc) {
        log.info("Started {}", this.getClass().getSimpleName());
        Element artifactLifecycleSettings =
                doc.getRootElement().getChild("artifactLifecycleSettings", doc.getRootElement().getNamespace());
        if (artifactLifecycleSettings != null) {
            doc.getRootElement().removeChild("artifactLifecycleSettings", doc.getRootElement().getNamespace());
            log.info("Successfully removed 'artifactLifecycleSettings'");
        }
        log.info("Finished {}", this.getClass().getSimpleName());
    }
}
