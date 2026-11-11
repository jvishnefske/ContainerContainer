package org.artifactory.version.converter.v334;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jfrog.access.client.model.SamlSetting;

import static org.artifactory.config.AccessConfigKeys.SAML_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.config.AccessConfigKeys.SamlConfigKeys.AUTO_REDIRECT;
import static org.artifactory.config.AccessConfigKeys.SamlConfigKeys.NO_AUTO_USER_CREATION;
import static org.artifactory.config.AccessConfigKeys.SamlConfigKeys.CERTIFICATE;
import static org.artifactory.config.AccessConfigKeys.SamlConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE;
import static org.artifactory.config.AccessConfigKeys.SamlConfigKeys.EMAIL_ATTRIBUTE;
import static org.artifactory.config.AccessConfigKeys.SamlConfigKeys.ENABLE_INTEGRATION;
import static org.artifactory.config.AccessConfigKeys.SamlConfigKeys.GROUP_ATTRIBUTE;
import static org.artifactory.config.AccessConfigKeys.SamlConfigKeys.LOGIN_URL;
import static org.artifactory.config.AccessConfigKeys.SamlConfigKeys.LOGOUT_URL;
import static org.artifactory.config.AccessConfigKeys.SamlConfigKeys.SERVICE_PROVIDER_NAME;
import static org.artifactory.config.AccessConfigKeys.SamlConfigKeys.SYNC_GROUPS;
import static org.artifactory.config.AccessConfigKeys.SamlConfigKeys.USE_ENCRYPTED_ASSERTION;
import static org.artifactory.config.AccessConfigKeys.SamlConfigKeys.VERIFY_AUDIENCE_RESTRICTION;

@Slf4j
public class MoveSamlSettingToAccessConverter implements XmlConverter {

    private static final String GOING_TO_REMOVE = "Going to remove {}";
    private static final String SUCCESSFULLY_REMOVED = "Successfully removed {}";

    @Override
    public void convert(Document doc) {
        removeElements(doc);
    }

    private void removeElements(Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element security = rootElement.getChild(SECURITY, namespace);
        if (security != null) {
            Element samlSettingsElement = security.getChild(SAML_SETTINGS, namespace);
            if (samlSettingsElement != null) {
                boolean enableIntegration =
                        extractedAndRemoveBooleanProperty(samlSettingsElement, ENABLE_INTEGRATION, namespace);
                String loginUrl = extractedAndRemoveStringProperty(samlSettingsElement, LOGIN_URL, namespace);
                boolean syncGroups = extractedAndRemoveBooleanProperty(samlSettingsElement, SYNC_GROUPS, namespace);
                String logoutUrl = extractedAndRemoveStringProperty(samlSettingsElement, LOGOUT_URL, namespace);
                String serviceProviderName =
                        extractedAndRemoveStringProperty(samlSettingsElement, SERVICE_PROVIDER_NAME, namespace);
                String certificate = extractedAndRemoveStringProperty(samlSettingsElement, CERTIFICATE, namespace);
                boolean useEncryptedAssertion =
                        extractedAndRemoveBooleanProperty(samlSettingsElement, USE_ENCRYPTED_ASSERTION, namespace);
                String groupAttribute =
                        extractedAndRemoveStringProperty(samlSettingsElement, GROUP_ATTRIBUTE, namespace);
                String emailAttribute =
                        extractedAndRemoveStringProperty(samlSettingsElement, EMAIL_ATTRIBUTE, namespace);
                boolean autoUserCreation =
                        extractedAndRemoveBooleanPropertyDefaultTrue(samlSettingsElement,
                                NO_AUTO_USER_CREATION, namespace);
                boolean allowUserToAccessProfile =
                        extractedAndRemoveBooleanProperty(samlSettingsElement, ALLOW_USER_TO_ACCESS_PROFILE, namespace);
                boolean autoRedirect = extractedAndRemoveBooleanProperty(samlSettingsElement, AUTO_REDIRECT, namespace);
                boolean verifyAudienceRestriction =
                        extractedAndRemoveBooleanPropertyDefaultTrue(
                                samlSettingsElement, VERIFY_AUDIENCE_RESTRICTION, namespace);
                removeSamlRoot(security, samlSettingsElement, namespace);
                SamlSetting samlSetting = SamlSetting
                        .builder()
                        .enableIntegration(enableIntegration)
                        .loginUrl(loginUrl)
                        .logoutUrl(logoutUrl)
                        .serviceProviderName(serviceProviderName)
                        .certificate(certificate)
                        .useEncryptedAssertion(useEncryptedAssertion)
                        .syncGroups(syncGroups)
                        .groupAttribute(groupAttribute)
                        .emailAttribute(emailAttribute)
                        .autoUserCreation(autoUserCreation)
                        .allowUserToAccessProfile(allowUserToAccessProfile)
                        .autoRedirect(autoRedirect)
                        .verifyAudienceRestriction(verifyAudienceRestriction)
                        .build();
                AccessConfigHolder.getInstance().setSamlSetting(samlSetting);
            }
        }
    }

    private boolean extractedAndRemoveBooleanPropertyDefaultTrue(Element samlElement, String property,
            Namespace namespace) {
        log.info(GOING_TO_REMOVE, property);
        boolean booleanValue = true;
        if (samlElement.getChild(property, namespace) != null) {
            booleanValue = Boolean.parseBoolean(
                    samlElement.getChild(property, namespace).getValue());
        }
        samlElement.removeChild(property, namespace);
        log.info(SUCCESSFULLY_REMOVED, property);
        return booleanValue;
    }

    private boolean extractedAndRemoveBooleanProperty(Element samlElement, String property, Namespace namespace) {
        log.info(GOING_TO_REMOVE, property);
        boolean booleanValue = false;
        if (samlElement.getChild(property, namespace) != null) {
            booleanValue = Boolean.parseBoolean(
                    samlElement.getChild(property, namespace).getValue());
        }
        samlElement.removeChild(property, namespace);
        log.info(SUCCESSFULLY_REMOVED, property);
        return booleanValue;
    }

    private String extractedAndRemoveStringProperty(Element samlElement, String property, Namespace namespace) {
        log.info(GOING_TO_REMOVE, property);
        String stringValue = "";
        if (samlElement.getChild(property, namespace) != null) {
            stringValue = samlElement.getChild(property, namespace).getValue();
        }
        samlElement.removeChild(property, namespace);
        log.info(SUCCESSFULLY_REMOVED, property);
        return stringValue;
    }

    @SneakyThrows
    private void removeSamlRoot(Element security, Element samlSettingsElement, Namespace namespace) {
        log.info(GOING_TO_REMOVE, SAML_SETTINGS);
        if (samlSettingsElement.getChildren().isEmpty()) {
            security.removeChild(SAML_SETTINGS, namespace);
            log.info(SUCCESSFULLY_REMOVED, SAML_SETTINGS);
        } else {
            String message = "Some elements still exist under node " + SAML_SETTINGS
                    + " - can't delete it";
            log.error(message);
            throw new ConverterPreconditionException(message);
        }
    }
}
