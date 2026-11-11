package org.artifactory.version.converter.v332;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import static org.artifactory.version.converter.v332.SetXrayTokenIdentificationToTrueConverter.USE_ONLY_TOKEN_FOR_IDENTIFYING_XRAY_USER;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SetXrayTokenIdentificationToTrueConverterTest extends XmlConverterTest {

    private static final String ELEMENT_EXISTS_RESOURCE_PATH =
            "/config/test/config.3.3.2_set_xray_token_ident_to_true.xml";

    private static final String ELEMENT_EXISTS_AND_TRUE_RESOURCE_PATH =
            "/config/test/config.3.3.2_set_xray_token_ident_if_already_true.xml";

    @Test
    void conversionWhenElementExists() throws Exception {
        assertConversionSuccessfull(ELEMENT_EXISTS_RESOURCE_PATH);
    }

    @Test
    void conversionWhenElementExistsAndTrue() throws Exception {
        assertConversionSuccessfull(ELEMENT_EXISTS_AND_TRUE_RESOURCE_PATH);
    }

    private void assertConversionSuccessfull(String pathToXml) throws Exception {
        Document doc = convertXml(pathToXml, new SetXrayTokenIdentificationToTrueConverter());
        Element root = doc.getRootElement();
        Namespace namespace = root.getNamespace();
        Element element = root.getChild(USE_ONLY_TOKEN_FOR_IDENTIFYING_XRAY_USER, namespace);
        assertEquals("true", element.getText());
    }
}