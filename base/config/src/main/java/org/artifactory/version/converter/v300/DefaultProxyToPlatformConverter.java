package org.artifactory.version.converter.v300;

import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts each proxy 'defaultProxy' field to 'platformDefault' field, with same value.
 *
 * @author Yuval Reches
 */
public class DefaultProxyToPlatformConverter implements XmlConverter {
    private static final Logger log = LoggerFactory.getLogger(DefaultProxyToPlatformConverter.class);

    @Override
    public void convert(Document doc) {
        log.info("Starting 'defaultProxy' to 'platformDefault' conversion");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element proxies = rootElement.getChild("proxies", namespace);
        if (proxies != null) {
            convertProxies(proxies, namespace);
        }
        log.info("Finished 'defaultProxy' to 'platformDefault' conversion");
    }

    /**
     * In case "defaultProxy" field exists:
     * - adds "platformDefault" with "defaultProxy" value
     * - deletes "platformDefault" field
     */
    private void convertProxies(Element proxiesElement, Namespace namespace) {
        proxiesElement.getChildren().forEach(proxy -> {
            Element defaultProxy = proxy.getChild("defaultProxy", namespace);
            if (defaultProxy != null) {
                createPlatformDefaultField(proxy, namespace, defaultProxy.getValue());
                proxy.removeChild("defaultProxy", namespace);
            }
        });
    }

    private void createPlatformDefaultField(Element proxyElement, Namespace namespace, String defaultProxyValue) {
        // Creating new field and setting value according to old field value
        Element platformDefault = new Element("platformDefault", namespace).setText(defaultProxyValue);
        proxyElement.addContent(new Text("\n        "));
        proxyElement.addContent(platformDefault);
    }
}
