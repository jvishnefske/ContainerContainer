package org.artifactory.version.converter.v338;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.XMLOutputter;

/**
 * @author vovam
 */
@Slf4j
public class RemoveCleanupPoliciesSettingsConvertor implements XmlConverter {
    private static final String CLEANUP_SETTINGS_FIELD_NAME = "cleanupPoliciesSettings";

    @Override
    public void convert(Document doc) {
        log.info("Starting remove beta package cleanup policies settings convertor.");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element cleanupPoliciesSettingsElement = rootElement.getChild(CLEANUP_SETTINGS_FIELD_NAME, namespace);
        if (cleanupPoliciesSettingsElement == null) {
            log.info("No beta package cleanup policies settings. Nothing to delete");
        } else {
            printCleanupSettings(cleanupPoliciesSettingsElement);
            rootElement.removeChildren(CLEANUP_SETTINGS_FIELD_NAME, namespace);
        }
    }

    private void printCleanupSettings(Element cleanupPoliciesSettings) {
        try {
            XMLOutputter out = new XMLOutputter();
            String cleanupPoliciesSettingsAsString = out.outputString(cleanupPoliciesSettings);
            log.info("Beta package cleanup policies for deletion: \n '{}'", cleanupPoliciesSettingsAsString);
        } catch (Exception e) {
            log.warn("Can't transform beta package cleanup policies settings to string to print it");
        }
    }
}