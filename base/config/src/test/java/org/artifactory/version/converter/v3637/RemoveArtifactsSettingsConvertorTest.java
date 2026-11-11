package org.artifactory.version.converter.v3637;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import static org.testng.AssertJUnit.assertNull;


/**
 * @author Hussein Alshqerat
 */
class RemoveArtifactsSettingsConvertorTest extends XmlConverterTest {

    private static final String RESOURCE_PATH = "/config/test/config.3.1.36_remove_artifacts_settings.xml";
    private static final String ARTIFACTS_SETTINGS = "artifactsSettings";

    @Test
    void testConvert() throws Exception {
        Document doc = convertXml(RESOURCE_PATH,
                new RemoveArtifactsSettingsConvertor());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        assertNull(rootElement.getChild(ARTIFACTS_SETTINGS, namespace));
    }
}