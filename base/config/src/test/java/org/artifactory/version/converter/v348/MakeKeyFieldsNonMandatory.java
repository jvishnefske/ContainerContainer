package org.artifactory.version.converter.v348;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MakeKeyFieldsNonMandatory extends XmlConverterTest {

    @Test
    void testMissingPrivateKey() throws Exception {
        Document document = getDocument("/config/test/config.3.4.8_make_key_fields_non_mandatory.xml");

        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element keyPairs = rootElement.getChild("keyPairs", namespace);
        Element keyPair = keyPairs.getChild("keyPair", namespace);
        String privateKey = keyPair.getChildText("privateKey", namespace);
        String publicKey = keyPair.getChildText("publicKey", namespace);

        assertNotNull(publicKey);
        assertNull(privateKey);
    }
}
