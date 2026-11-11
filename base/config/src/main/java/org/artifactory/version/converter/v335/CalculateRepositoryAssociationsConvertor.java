package org.artifactory.version.converter.v335;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

/**
 * @author Amir Avidan
 */
@Slf4j
public class CalculateRepositoryAssociationsConvertor implements XmlConverter {

    @Override
    public void convert(Document doc) {
        log.info("Starting add calculate repository associations convertor.");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element calculateRepositoryAssociations = rootElement.getChild("calculateRepositoryAssociations", namespace);
        if (calculateRepositoryAssociations == null) {
            addCalculateRepositoryAssociations(rootElement, namespace);
            log.info("Finished calculate repository associations conversion successfully");
        }
    }

    private void addCalculateRepositoryAssociations(Element rootElement, Namespace namespace) {
        Element calculateRepositoryAssociations = new Element("calculateRepositoryAssociations", namespace);
        calculateRepositoryAssociations.addContent("true");
        Element trashcanConfig = rootElement.getChild("trashcanConfig", namespace);
        if (trashcanConfig != null) {
            rootElement.addContent(rootElement.indexOf(trashcanConfig) + 1, calculateRepositoryAssociations);
        }
    }
}
