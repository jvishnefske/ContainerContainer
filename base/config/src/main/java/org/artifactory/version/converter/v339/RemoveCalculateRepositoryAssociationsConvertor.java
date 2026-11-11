package org.artifactory.version.converter.v339;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

/**
 * @author Amir Avidan
 */
@Slf4j
public class RemoveCalculateRepositoryAssociationsConvertor implements XmlConverter {

    @Override
    public void convert(Document doc) {
        log.info("Started remove calculateRepositoryAssociations convertor.");
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        Element artifactsSettings = root.getChild("calculateRepositoryAssociations", ns);
        if (artifactsSettings != null) {
            root.removeChild("calculateRepositoryAssociations", ns);
            log.info("Successfully removed calculate repository associations");
        }
        log.info("Finished remove calculate repository associations convertor.");
    }
}
