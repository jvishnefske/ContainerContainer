package org.artifactory.version.converter.v332;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

@Slf4j
public class SetXrayTokenIdentificationToTrueConverter implements XmlConverter {

    static final String USE_ONLY_TOKEN_FOR_IDENTIFYING_XRAY_USER = "useOnlyTokenForIdentifyingXrayUser";

    @Override
    public void convert(Document doc) {
        log.info("SetXrayTokenIdentificationToTrueConverter started");
        Element root = doc.getRootElement();
        Namespace namespace = root.getNamespace();
        Element existent = root.getChild(USE_ONLY_TOKEN_FOR_IDENTIFYING_XRAY_USER, namespace);
        if (existent != null) {
            log.info(USE_ONLY_TOKEN_FOR_IDENTIFYING_XRAY_USER + " found. Text is [{}]", existent.getText());
            existent.setText(Boolean.TRUE.toString());
        } else {
            log.info(USE_ONLY_TOKEN_FOR_IDENTIFYING_XRAY_USER + " not found");
        }
        log.info("SetXrayTokenIdentificationToTrueConverter finished");
    }
}
