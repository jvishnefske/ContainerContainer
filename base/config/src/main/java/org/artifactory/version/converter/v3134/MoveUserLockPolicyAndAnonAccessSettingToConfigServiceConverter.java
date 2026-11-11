package org.artifactory.version.converter.v3134;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

@Slf4j
public class MoveUserLockPolicyAndAnonAccessSettingToConfigServiceConverter implements XmlConverter {

    @Override
    public void convert(Document doc) {
        removeElements(doc);
    }

    private void removeElements(Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element security = rootElement.getChild("security", namespace);
        if (security != null) {
            moveUserLockPolicy(security, namespace);
            moveAnonymousAccessEnabled(security, namespace);
        }
    }

    private void moveUserLockPolicy(Element security, Namespace namespace) {
        String tagName = "userLockPolicy";
        Element elementToRemove = security.getChild(tagName, namespace);
        if (elementToRemove != null) {
            if (elementToRemove.getChild("enabled", namespace) != null) {
                boolean isEnabled = Boolean.parseBoolean(elementToRemove.getChild("enabled", namespace).getValue());
                int loginAttempts =
                        isEnabled ? Integer.parseInt(elementToRemove.getChild("loginAttempts", namespace).getValue()) :
                                0;
                AccessConfigHolder.getInstance().addLoginAttempts(loginAttempts);
            }
            security.removeChild(tagName, namespace);
            log.info("Successfully removed " + tagName);
        }
    }

    private void moveAnonymousAccessEnabled(Element security, Namespace ns) {
        String tagName = "anonAccessEnabled";
        Element elementToRemove = security.getChild(tagName, ns);
        if (elementToRemove != null) {
            AccessConfigHolder.getInstance().addAnonAccessEnabled(Boolean.valueOf(elementToRemove.getValue()));
            security.removeChild(tagName, ns);
            log.info("Successfully removed " + tagName);
        }
    }
}
