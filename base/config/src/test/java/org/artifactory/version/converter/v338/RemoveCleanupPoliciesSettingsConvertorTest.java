package org.artifactory.version.converter.v338;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class RemoveCleanupPoliciesSettingsConvertorTest extends XmlConverterTest {

    @Test
    public void testConverterWithoutCleanupPolicies() throws Exception {
        Document document = convertXml("/config/test/config.3.3.3_add_items_browser_paging_size.xml",
                new RemoveCleanupPoliciesSettingsConvertor());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element cleanupPoliciesSettings = rootElement.getChild("cleanupPoliciesSettings", namespace);
        assertNull(cleanupPoliciesSettings);
    }

    @Test
    public void testConverterWithCleanupPolicies() throws Exception {
        Document document = convertXml("/config/test/config.3.3.8_remove_cleanup_policies_settings.xml",
                new RemoveCleanupPoliciesSettingsConvertor());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element itemsBrowserPagingSize = rootElement.getChild("cleanupPoliciesSettings", namespace);
        assertNull(itemsBrowserPagingSize);
    }

    @Test
    public void testWIthoutConverter() throws Exception {
        Document document = convertXml("/config/test/config.3.3.8_remove_cleanup_policies_settings.xml");
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element cleanupPoliciesSettings = rootElement.getChild("cleanupPoliciesSettings", namespace);
        assertNotNull(cleanupPoliciesSettings);
    }
}