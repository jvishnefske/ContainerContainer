package org.artifactory.version.converter.v300;

import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Also see: https://www.jfrog.com/jira/browse/RTFACT-19701
 *
 * Removes the 'proxyRef' and 'bypassDefaultProxy' from the Xray Descriptor, which are now obsolete with the new JPD
 * Deployment
 *
 * @author Uriah Levy
 */
public class RemoveXrayProxyConfigConverter implements XmlConverter {
    private static final Logger log = LoggerFactory.getLogger(RemoveXrayProxyConfigConverter.class);
    static final String PROXY_REF = "proxyRef";
    static final String BYPASS_DEFAULT_PROXY = "bypassDefaultProxy";
    static final String XRAY_CONFIG = "xrayConfig";

    @Override
    public void convert(Document doc) {
        log.info("Started {}", this.getClass().getSimpleName());
        Element xrayConfig = doc.getRootElement().getChild(XRAY_CONFIG, doc.getRootElement().getNamespace());
        if (xrayConfig != null) {
            if (xrayConfig.removeChild(PROXY_REF, doc.getRootElement().getNamespace())) {
                log.info("Successfully removed '{}' field from {}", PROXY_REF, XRAY_CONFIG);
                log.warn(
                        "Manual action required: the existing proxy configuration between Artifactory and Xray is " +
                                "obsolete since Artifactory version 7 and has been removed." +
                                " In order to set the proxy configuration, please refer to the following " +
                                "instructions: https://service.jfrog" +
                                ".org/installer/Artifactory+System+YAML#ArtifactorySystemYAML" +
                                "-ArtifactoryOperationalMicroservices");
            }
            if (xrayConfig.removeChild(BYPASS_DEFAULT_PROXY, doc.getRootElement().getNamespace())) {
                log.info("Successfully removed '{}' field from {}", BYPASS_DEFAULT_PROXY, XRAY_CONFIG);
            }
        }
        log.info("Finished {}", this.getClass().getSimpleName());
    }
}