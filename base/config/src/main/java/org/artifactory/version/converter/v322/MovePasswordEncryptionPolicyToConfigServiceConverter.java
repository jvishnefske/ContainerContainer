package org.artifactory.version.converter.v322;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.descriptor.security.EncryptionPolicy;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import java.util.Locale;

import static org.artifactory.config.AccessConfigKeys.PASSWORD_SETTINGS_ENCRYPTION_POLICY;
import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.config.AccessConfigKeys.SECURITY_PASSWORD_SETTINGS;

/**
 * @author Mor Merhav
 */
@Slf4j
public class MovePasswordEncryptionPolicyToConfigServiceConverter implements XmlConverter {
    @Override
    public void convert(Document doc) {
        removeElements(doc);
    }

    private void removeElements(Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element security = rootElement.getChild(SECURITY, namespace);
        if (security != null) {
            movePasswordSettings(security, namespace);
        }
    }

    private void movePasswordSettings(Element security, Namespace namespace) {
        Element passwordSettings = security.getChild(SECURITY_PASSWORD_SETTINGS, namespace);
        if (passwordSettings != null) {
            if (passwordSettings.getChild(PASSWORD_SETTINGS_ENCRYPTION_POLICY, namespace) != null) {
                EncryptionPolicy encryptionPolicy = EncryptionPolicy.valueOf(
                        passwordSettings.getChild(PASSWORD_SETTINGS_ENCRYPTION_POLICY, namespace).getValue()
                                .toUpperCase(Locale.ROOT));
                AccessConfigHolder.getInstance().addPasswordEncryptionPolicy(encryptionPolicy);
            }
            passwordSettings.removeChild(PASSWORD_SETTINGS_ENCRYPTION_POLICY, namespace);
            log.info("Successfully removed: {}", PASSWORD_SETTINGS_ENCRYPTION_POLICY);
        }
    }
}
