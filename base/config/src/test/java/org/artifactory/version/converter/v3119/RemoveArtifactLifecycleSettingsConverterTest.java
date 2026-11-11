package org.artifactory.version.converter.v3119;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertNull;

public class RemoveArtifactLifecycleSettingsConverterTest extends XmlConverterTest {

    @Test
    public void testConvert() throws Exception {
        Document doc = convertXml("/config/test/config.3.1.20_remove_artifact_lifecycle_settings.xml",
                new RemoveArtifactLifecycleSettingsConverter());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        assertNull(rootElement.getChild("artifactLifecycleSettings", namespace));
    }
}