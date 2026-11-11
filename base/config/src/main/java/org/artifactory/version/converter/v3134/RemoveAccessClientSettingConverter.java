package org.artifactory.version.converter.v3134;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

@Slf4j
public class RemoveAccessClientSettingConverter implements XmlConverter {

    public static final String SECURITY = "security";

    @Override
    public void convert(Document doc) {
        removeElements(doc, "accessClientSettings");
    }

    private void removeElements(Document doc, String fromTagName) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element security = rootElement.getChild(SECURITY, namespace);
        Element elementToRemove = security.getChild(fromTagName, namespace);
        if (elementToRemove != null) {
            security.removeChild(fromTagName, namespace);
            log.info("Successfully removed " + fromTagName);
        }

    }
}
