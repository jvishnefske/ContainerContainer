package org.artifactory.version.converter.v300;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Yuval Reches
 */
public class DefaultProxyToPlatformConverterTest extends XmlConverterTest {
    private static final String CONFIG_XML = "/config/test/config.2.2.3.with.proxies.xml";
    private static final String CONFIG_XML_NO_PROXIES = "/config/test/config.2.2.3.without.proxies.xml";
    private static final String CONFIG_XML_NO_DEFAULT = "/config/test/config.2.2.3.with.proxies.no.default.xml";

    @Test
    public void convertWithProxies() throws Exception {
        Document document = convertXml(CONFIG_XML, new DefaultProxyToPlatformConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        validateConfig(rootElement.getChild("proxies", namespace), namespace);
    }

    @Test
    public void convertWithProxiesNoDefault() throws Exception {
        Document document = convertXml(CONFIG_XML_NO_DEFAULT, new DefaultProxyToPlatformConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        validateConfigNoDefault(rootElement.getChild("proxies", namespace), namespace);
    }

    @Test
    public void convertWithNoProxies() throws Exception {
        Document document = convertXml(CONFIG_XML_NO_PROXIES, new DefaultProxyToPlatformConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        assertNull(rootElement.getChild("proxies", namespace));
    }

    private void validateConfig(Element proxies, Namespace namespace) {
        assertNotNull(proxies, "Proxies element should exist");
        proxies.getChildren().forEach(proxy -> {
            assertNull(proxy.getChild("defaultProxy", namespace));
            assertNotNull(proxy.getChild("platformDefault", namespace));
            boolean defaultValue = Boolean.parseBoolean(proxy.getChild("platformDefault", namespace).getText());
            if (proxy.getChild("key", namespace).getText().equals("Charles")) {
                assertTrue(defaultValue);
            } else {
                assertFalse(defaultValue);
            }
        });
    }

    private void validateConfigNoDefault(Element proxies, Namespace namespace) {
        assertNotNull(proxies, "Proxies element should exist");
        proxies.getChildren().forEach(proxy -> {
            assertNull(proxy.getChild("defaultProxy", namespace));
            assertNull(proxy.getChild("platformDefault", namespace));
        });
    }

}
