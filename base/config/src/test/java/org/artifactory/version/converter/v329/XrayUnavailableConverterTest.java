package org.artifactory.version.converter.v329;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class XrayUnavailableConverterTest extends XmlConverterTest {

    @Test
    public void testBeforeMigration() throws Exception {
        Document document = getDocument("/config/test/config.3.2.9_convert_xray_unavailable_operation.xml");
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element xrayConfig = rootElement.getChild("xrayConfig", namespace);
        assertEquals(xrayConfig.getChild("allowDownloadsXrayUnavailable", namespace).getValue(), "false");
        assertNull(xrayConfig.getChild("allowOperationsXrayUnavailable", namespace));
    }

    @Test
    public void testAfterMigration() throws Exception {
        Document document = convertXml("/config/test/config.3.2.9_convert_xray_unavailable_operation.xml",
                new XrayUnavailableConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element xrayConfig = rootElement.getChild("xrayConfig", namespace);
        assertNull(xrayConfig.getChild("allowDownloadsXrayUnavailable", namespace));
        assertEquals(xrayConfig.getChild("allowOperationsXrayUnavailable", namespace).getValue(), "false");
    }

}
