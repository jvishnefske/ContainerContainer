package org.artifactory.version.converter.v3132;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

/**
 * Adds the artifactsSettings element to the config descriptor
 *
 * @author Hussein Alshqerat
 */

@Slf4j
public class AddArtifactsSettingsConverter implements XmlConverter {

    @Override
    public void convert(Document doc) {
        log.info("Starting 'Add artifacts settings' conversion");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element artifactsSettings = rootElement.getChild("artifactsSettings", namespace);
        if (artifactsSettings == null) {
            addArtifactsSettings(rootElement, namespace);
        }
    }

    private void addArtifactsSettings(Element rootElement, Namespace namespace) {
        Element artifactsSettings = new Element("artifactsSettings", namespace);
        Element showArtifactsCountMessage = new Element("showCountMessage", namespace);
        showArtifactsCountMessage.addContent("true");
        int trashcanLocation = findLastLocation(rootElement, "trashcanConfig");
        artifactsSettings.addContent(showArtifactsCountMessage);
        rootElement.addContent(trashcanLocation + 1, artifactsSettings);
        log.info("Finished 'Add artifacts settings' conversion successfully");
    }

}

