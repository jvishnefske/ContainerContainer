package org.artifactory.version.converter.v3134;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

@Slf4j
public class CopyPasswordExpirationPolicyToConfigServiceConverter implements XmlConverter {

    @Override
    public void convert(Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element security = rootElement.getChild("security", namespace);
        if (security == null) {
            return;
        }
        Element passwordSettings = security.getChild("passwordSettings", namespace);
        if (passwordSettings == null) {
            return;
        }
        Element expirationPolicy = passwordSettings.getChild("expirationPolicy", namespace);
        if (expirationPolicy != null && expirationPolicy.getChild("enabled", namespace) != null) {
            boolean isEnabled = Boolean.parseBoolean(expirationPolicy.getChild("enabled", namespace).getValue());
            int passwordMaxAge =
                    isEnabled ? Integer.parseInt(expirationPolicy.getChild("passwordMaxAge", namespace).getValue()) : 0;
            AccessConfigHolder.getInstance().addPasswordExpiryDays(passwordMaxAge);
            log.info("Successfully copied " + expirationPolicy);
        }
    }
}
