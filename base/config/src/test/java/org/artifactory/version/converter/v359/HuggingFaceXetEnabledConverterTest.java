package org.artifactory.version.converter.v359;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * @author shahafg
 */
public class HuggingFaceXetEnabledConverterTest extends XmlConverterTest {

    private static final String XET_REMOTE_ENABLED = "xetRemoteEnabled";
    private static final String XET_REMOTE_ENABLED_EXPLICIT = "xetRemoteEnabledExplicit";
    private static final String XET_ENABLED = "xetEnabled";
    private static final String XET_ENABLED_EXPLICIT = "xetEnabledExplicit";

    @Test
    public void testConvertRenamesLegacyEnabledElements() throws Exception {
        Element hf = convertAndGetHuggingface(
                "/config/test/config.3.5.8_with_huggingface_xet_remote_enabled.xml");
        Namespace ns = hf.getNamespace();

        assertNull(hf.getChild(XET_REMOTE_ENABLED, ns), "Legacy element should be renamed away");
        assertNull(hf.getChild(XET_REMOTE_ENABLED_EXPLICIT, ns), "Legacy element should be renamed away");
        assertNotNull(hf.getChild(XET_ENABLED, ns), "New element should exist");
        assertNotNull(hf.getChild(XET_ENABLED_EXPLICIT, ns), "New element should exist");
        assertEquals(hf.getChild(XET_ENABLED, ns).getText(), "true", "Value should be preserved");
        assertEquals(hf.getChild(XET_ENABLED_EXPLICIT, ns).getText(), "true", "Value should be preserved");
    }

    @Test
    public void testConvertPreservesDisabledValue() throws Exception {
        Element hf = convertAndGetHuggingface(
                "/config/test/config.3.5.8_with_huggingface_xet_remote_disabled.xml");
        Namespace ns = hf.getNamespace();

        assertNotNull(hf.getChild(XET_ENABLED, ns), "New element should exist");
        assertEquals(hf.getChild(XET_ENABLED, ns).getText(), "false", "Disabled value should be preserved");
        assertEquals(hf.getChild(XET_ENABLED_EXPLICIT, ns).getText(), "true", "Explicit value should be preserved");
    }

    @Test
    public void testConvertIsNoOpWhenLegacyElementsAbsent() throws Exception {
        Element hf = convertAndGetHuggingface(
                "/config/test/config.3.5.8_without_huggingface_xet.xml");
        Namespace ns = hf.getNamespace();

        assertNull(hf.getChild(XET_ENABLED, ns), "No new element should be created when legacy elements are absent");
        assertNull(hf.getChild(XET_ENABLED_EXPLICIT, ns),
                "No new element should be created when legacy elements are absent");
    }

    private Element convertAndGetHuggingface(String resourcePath) throws Exception {
        Document doc = convertXml(resourcePath, new HuggingFaceXetEnabledConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        Element repositoriesGeneralConfig = root.getChild("repositoriesGeneralConfig", ns);
        assertNotNull(repositoriesGeneralConfig, "repositoriesGeneralConfig should exist");
        Element hf = repositoriesGeneralConfig.getChild("huggingfaceml", ns);
        assertNotNull(hf, "huggingfaceml should exist");
        return hf;
    }
}
