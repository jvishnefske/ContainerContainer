package org.artifactory.version.converter.v3637;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

/**
 * removes the artifactsSettings element from the config descriptor.
 *
 * @author Hussein Alshqerat
 */
@Slf4j
public class RemoveArtifactsSettingsConvertor implements XmlConverter {
    private static final String ARTIFACTS_SETTINGS = "artifactsSettings";

    @Override
    public void convert(Document doc) {
        log.info("Started remove artifacts setting convertor.");
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        Element artifactsSettings = root.getChild(ARTIFACTS_SETTINGS, ns);
        if (artifactsSettings != null) {
            root.removeChild(ARTIFACTS_SETTINGS, ns);
            log.info("Successfully removed artifacts settings");
        }
        log.info("Finished remove artifacts setting convertor.");
    }
}