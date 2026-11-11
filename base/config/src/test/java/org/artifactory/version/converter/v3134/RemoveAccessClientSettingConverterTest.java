package org.artifactory.version.converter.v3134;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import static org.artifactory.version.converter.v3134.RemoveAccessClientSettingConverter.SECURITY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoveAccessClientSettingConverterTest extends XmlConverterTest {
    private static final String RESOURCE_PATH =
            "/config/test/config.3.1.32_removeAccessConfigAndAnonAccessEnabledAndUserLockPolicy.xml";

    @Test
    void testConvert() throws Exception {
        verifyContent(true, getDocument(RESOURCE_PATH));
        Document doc = convertXml(RESOURCE_PATH,
                new RemoveAccessClientSettingConverter());
        verifyContent(false, doc);
    }

    private void verifyContent(Boolean expectedValue, Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element security = rootElement.getChild(SECURITY, namespace);
        Element elementToRemove = security.getChild("accessClientSettings", namespace);
        if (elementToRemove != null) {
            assertTrue(expectedValue);
        }else {
            assertFalse(expectedValue);
        }
    }
}
