package org.artifactory.version.converter.v329;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;

/**
 * Renames the element "allowDownloadsXrayUnavailable" to "allowOperationsXrayUnavailable".
 * This element used for download, distribute and promote operations.
 *
 * @author Osaid Wattad
 */
@Slf4j
public class XrayUnavailableConverter implements XmlConverter {

    private static final String ROOT_ELEMENT = "xrayConfig";
    private static final String FIELD_NAME = "allowDownloadsXrayUnavailable";
    private static final String NEW_FIELD_NAME = "allowOperationsXrayUnavailable";

    @Override
    public void convert(Document doc) {
        Element root = doc.getRootElement();
        Element xrayConfigElement = root.getChild(ROOT_ELEMENT, root.getNamespace());
        if (xrayConfigElement != null) {
            log.info("Element {} found and converted", ROOT_ELEMENT);
            Element oldElement = xrayConfigElement.getChild(FIELD_NAME, root.getNamespace());
            if (oldElement != null) {
                oldElement.setName(NEW_FIELD_NAME);
                log.info("Element {} found and converted", FIELD_NAME);
            } else {
                log.info("Element {} not found", FIELD_NAME);
            }
        }
    }
}
