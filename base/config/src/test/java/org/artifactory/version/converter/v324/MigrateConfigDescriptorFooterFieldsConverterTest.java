package org.artifactory.version.converter.v324;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import static org.artifactory.platform.PlatformConfigKeys.DATE_FORMAT;
import static org.artifactory.platform.PlatformConfigKeys.HELP_LINKS_ENABLED;
import static org.artifactory.platform.PlatformConfigKeys.LOGO;
import static org.artifactory.platform.PlatformConfigKeys.SYSTEM_MESSAGE;
import static org.artifactory.platform.PlatformConfigKeys.SYSTEM_MESSAGE_CONFIG;
import static org.artifactory.platform.PlatformConfigKeys.SYSTEM_MESSAGE_ENABLED;
import static org.artifactory.platform.PlatformConfigKeys.SYSTEM_MESSAGE_TITLE;
import static org.artifactory.platform.PlatformConfigKeys.SYSTEM_MESSAGE_TITLE_COLOR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * @author Andrey Tashlitsky
 */
public class MigrateConfigDescriptorFooterFieldsConverterTest extends XmlConverterTest {

    @Test
    public void testBeforeMigration() throws Exception {
        Document document = getDocument("/config/test/config.3.2.4_migrate_footer_fields.xml");
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        assertEquals(rootElement.getChild(DATE_FORMAT, namespace).getValue(), "dd-MM-yy HH:mm:ss z");
        assertEquals(rootElement.getChild(HELP_LINKS_ENABLED, namespace).getValue(), "true");
        assertEquals(rootElement.getChild(LOGO, namespace).getValue(),
                "http://www.rw-designer.com/icon-image/21555-128x128x32.png");
        Element systemMessageElement = rootElement.getChild(SYSTEM_MESSAGE_CONFIG, namespace);
        assertEquals(systemMessageElement.getChild(SYSTEM_MESSAGE_ENABLED, namespace).getValue(), "true");
        assertEquals(systemMessageElement.getChild(SYSTEM_MESSAGE_TITLE, namespace).getValue(), "COOL");
        assertEquals(systemMessageElement.getChild(SYSTEM_MESSAGE_TITLE_COLOR, namespace).getValue(), "#429f46");
        assertEquals(systemMessageElement.getChild(SYSTEM_MESSAGE, namespace).getValue(), "Hello World!");
    }

    @Test
    public void testAfterMigration() throws Exception {
        Document document = convertXml("/config/test/config.3.2.4_migrate_footer_fields.xml",
                new MigrateConfigDescriptorFooterFieldsConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        assertNull(rootElement.getChild(DATE_FORMAT, namespace));
        assertNull(rootElement.getChild(HELP_LINKS_ENABLED, namespace));
        assertNull(rootElement.getChild(LOGO, namespace));
        assertNull(rootElement.getChild(SYSTEM_MESSAGE_CONFIG, namespace));
    }
}
