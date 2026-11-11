package org.artifactory.version.converter.v315;

import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This converter will add to the config descriptor the archive index enabled value set to true to preserve previous
 * behaviour.
 *
 * @author Alexei Vainshtein
 */
public class ArchiveIndexEnableConverter implements XmlConverter {

    private static final Logger log = LoggerFactory.getLogger(ArchiveIndexEnableConverter.class);

    @Override
    public void convert(Document doc) {
        log.info("Starting 'archiveIndexEnabled' conversion");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element archiveIndexEnabled = rootElement.getChild("archiveIndexEnabled", namespace);
        if (archiveIndexEnabled == null) {
            addArchiveIndexElement(rootElement, namespace);
        }
        log.info("Finished adding `archiveIndexEnabled`");
    }

    private void addArchiveIndexElement(Element rootElement, Namespace namespace) {
        Element archiveIndexEnabled;
        log.debug("No 'archiveIndexEnabled' element were found. Creating.");
        archiveIndexEnabled = new Element("archiveIndexEnabled", namespace);
        archiveIndexEnabled.setText(String.valueOf(true));
        int offlineModeLocation = findLastLocation(rootElement, "offlineMode");
        if (offlineModeLocation > 0) {
            rootElement.addContent(offlineModeLocation + 1, new Text("\n            "));
            rootElement.addContent(offlineModeLocation + 2, archiveIndexEnabled);
        } else {
            int serverNameLocation = findLastLocation(rootElement, "serverName");
            rootElement.addContent(serverNameLocation + 1, new Text("\n            "));
            rootElement.addContent(serverNameLocation + 2, archiveIndexEnabled);
        }
    }
}
