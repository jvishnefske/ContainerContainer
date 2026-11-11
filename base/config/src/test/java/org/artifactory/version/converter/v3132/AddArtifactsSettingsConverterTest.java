package org.artifactory.version.converter.v3132;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author Hussein Alshqerat
 */
public class AddArtifactsSettingsConverterTest extends XmlConverterTest {

    private static final String RESOURCE_PATH = "/config/test/config.3.1.31_add_artifacts_settings.xml";

    @Test
    public void testConvertWithAddingArtifactsSettings() throws Exception {
        Document doc = convertXml(RESOURCE_PATH,
                new AddArtifactsSettingsConverter());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element artifactsSettings = rootElement.getChild("artifactsSettings", namespace);
        assertEquals(artifactsSettings.getChild("showCountMessage", namespace).getValue(), "true");
    }

}