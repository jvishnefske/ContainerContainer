package org.artifactory.version.converter.v300;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import static org.artifactory.version.converter.v300.RemoveXrayProxyConfigConverter.BYPASS_DEFAULT_PROXY;
import static org.artifactory.version.converter.v300.RemoveXrayProxyConfigConverter.PROXY_REF;
import static org.artifactory.version.converter.v300.RemoveXrayProxyConfigConverter.XRAY_CONFIG;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * Tests {@link RemoveXrayProxyConfigConverter}
 *
 * @author Uriah Levy
 */
@Test
public class RemoveXrayProxyConfigConverterTest extends XmlConverterTest {

    public void testRemoveProxyRefAndBypassDefaultProxyFields() throws Exception {
        Document doc = convertXml("/config/test/config.2.2.3.with.xray.proxy.xml",
                new RemoveXrayProxyConfigConverter());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element xrayConfig = rootElement.getChild(XRAY_CONFIG, namespace);

        assertNotNull(xrayConfig);
        assertNull(xrayConfig.getChild(PROXY_REF));
        assertNull(xrayConfig.getChild(BYPASS_DEFAULT_PROXY));
    }
}