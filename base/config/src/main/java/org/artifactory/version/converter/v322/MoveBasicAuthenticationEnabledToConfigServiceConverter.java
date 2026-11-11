package org.artifactory.version.converter.v322;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.config.AccessConfigKeys.SECURITY_BASIC_AUTH_ENABLED;

/**
 * @author Mor Merhav
 */
@Slf4j
public class MoveBasicAuthenticationEnabledToConfigServiceConverter implements XmlConverter {

    @Override
    public void convert(@NonNull Document doc) {
        String simpleName = this.getClass().getSimpleName();
        log.info("Started {}", simpleName);
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element securityElement = rootElement.getChild(SECURITY, namespace);
        if (securityElement != null) {
            Element basicAuthEnabledElement = securityElement.getChild(SECURITY_BASIC_AUTH_ENABLED, namespace);
            if (basicAuthEnabledElement != null) {
                boolean basicAuthEnabled = Boolean.parseBoolean(basicAuthEnabledElement.getValue());
                AccessConfigHolder.getInstance().addBasicAuthenticationEnabled(basicAuthEnabled);
            }
            securityElement.removeChild(SECURITY_BASIC_AUTH_ENABLED, namespace);
            log.info("Successfully removed: {}", SECURITY_BASIC_AUTH_ENABLED);
        }
        log.info("Finished {}", simpleName);
    }
}