package org.artifactory.version.converter.v333;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * @author Hussein Alshqerat
 */
public class ItemsBrowserPagingSizeConvertorTest extends XmlConverterTest {

    @Test
    public void testBeforeMigration() throws Exception {
        Document document = getDocument("/config/test/config.3.3.3_add_items_browser_paging_size.xml");
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element itemsBrowserPagingSize = rootElement.getChild("itemsBrowserPagingSize", namespace);
        assertNull(itemsBrowserPagingSize);
    }

    @Test
    public void testAfterMigration() throws Exception {
        Document document = convertXml("/config/test/config.3.3.3_add_items_browser_paging_size.xml",
                new ItemsBrowserPagingSizeConvertor());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element itemsBrowserPagingSize = rootElement.getChild("itemsBrowserPagingSize", namespace);
        assertNotNull(itemsBrowserPagingSize);
        assertEquals(itemsBrowserPagingSize.getValue(), "500");
    }
}