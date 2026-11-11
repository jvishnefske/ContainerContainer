package org.artifactory.version.converter.v327;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.config.AccessConfigKeys.CrowdConfigKeys;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jfrog.access.client.model.CrowdSetting;

import static org.artifactory.config.AccessConfigKeys.CROWD_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.SECURITY;


@Slf4j
public class MigrateCrowdSettingsToAccessServiceConverter implements XmlConverter {
    @Override
    public void convert(Document doc) {
        removeElements(doc);
    }

    private void removeElements(Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element security = rootElement.getChild(SECURITY, namespace);
        if (security != null) {
            Element crowdSettingsElement = security.getChild(CROWD_SETTINGS, namespace);
            if (crowdSettingsElement != null) {
                boolean enableIntegration = moveEnableIntegration(crowdSettingsElement, namespace);
                String serverUrl = moveServerUrl(crowdSettingsElement, namespace);
                boolean autoCreation = moveNoAutoCreation(crowdSettingsElement, namespace);
                String applicationName = moveApplicationName(crowdSettingsElement, namespace);
                String password = movePassword(crowdSettingsElement, namespace);
                long sessionValidationInterval = moveSessionValidationInterval(crowdSettingsElement, namespace);
                boolean useDefaultProxy = moveDefaultProxy(crowdSettingsElement, namespace);
                boolean allowUserToAccessProfile = moveAllowUserToAccessProfile(crowdSettingsElement, namespace);
                String customCookieTokenKey = moveCustomCookieTokenKey(crowdSettingsElement, namespace);
                boolean directAuthentication = moveDirectAuthentication(crowdSettingsElement, namespace);
                boolean overrideAllGroupsUponLogin = moveOverrideAllGroupsUponLogin(crowdSettingsElement, namespace);
                removeCrowdRoot(security, crowdSettingsElement, namespace);
                CrowdSetting crowdSetting = CrowdSetting
                        .builder()
                        .enable(enableIntegration)
                        .serverUrl(serverUrl)
                        .applicationName(applicationName)
                        .password(password)
                        .sessionValidationInterval(sessionValidationInterval)
                        .useDefaultProxy(useDefaultProxy)
                        .autoUserCreation(autoCreation)
                        .allowUserToAccessProfile(allowUserToAccessProfile)
                        .customCookieTokenKey(customCookieTokenKey)
                        .directAuthentication(directAuthentication)
                        .overrideAllGroupsUponLogin(overrideAllGroupsUponLogin)
                        .build();
                AccessConfigHolder.getInstance().setCrowdSetting(crowdSetting);
            }
        }
    }

    private boolean moveEnableIntegration(Element crowdSettingsElement, Namespace namespace) {
        boolean enableIntegration = false;
        if (crowdSettingsElement.getChild(CrowdConfigKeys.ENABLE_INTEGRATION, namespace) != null) {
            enableIntegration = Boolean.parseBoolean(
                    crowdSettingsElement.getChild(CrowdConfigKeys.ENABLE_INTEGRATION, namespace).getValue());
        }
        crowdSettingsElement.removeChild(CrowdConfigKeys.ENABLE_INTEGRATION, namespace);
        log.info("Successfully removed " + CrowdConfigKeys.ENABLE_INTEGRATION);
        return enableIntegration;
    }

    private boolean moveNoAutoCreation(Element crowdSettingsElement, Namespace namespace) {
        boolean noAutoCreation = false;
        if (crowdSettingsElement.getChild(CrowdConfigKeys.NO_AUTO_CREATION, namespace) != null) {
            noAutoCreation = Boolean.parseBoolean(
                    crowdSettingsElement.getChild(CrowdConfigKeys.NO_AUTO_CREATION, namespace).getValue());
        }
        crowdSettingsElement.removeChild(CrowdConfigKeys.NO_AUTO_CREATION, namespace);
        log.info("Successfully removed " + CrowdConfigKeys.NO_AUTO_CREATION);
        return noAutoCreation;
    }
    private boolean moveAllowUserToAccessProfile(Element crowdSettingsElement, Namespace namespace) {
        boolean allowUserToAccessProfile = false;
        if (crowdSettingsElement.getChild(CrowdConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE, namespace) != null) {
            allowUserToAccessProfile = Boolean.parseBoolean(crowdSettingsElement
                    .getChild(CrowdConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE, namespace).getValue());
        }
        crowdSettingsElement.removeChild(CrowdConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE, namespace);
        log.info("Successfully removed " + CrowdConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE);
        return allowUserToAccessProfile;
    }

    private boolean moveDefaultProxy(Element crowdSettingsElement, Namespace namespace) {
        boolean defaultProxy = false;
        if (crowdSettingsElement.getChild(CrowdConfigKeys.USE_DEFAULT_PROXY, namespace) != null) {
            defaultProxy = Boolean.parseBoolean(crowdSettingsElement
                    .getChild(CrowdConfigKeys.USE_DEFAULT_PROXY, namespace).getValue());
        }
        crowdSettingsElement.removeChild(CrowdConfigKeys.USE_DEFAULT_PROXY, namespace);
        log.info("Successfully removed " + CrowdConfigKeys.USE_DEFAULT_PROXY);
        return defaultProxy;
    }

    private boolean moveDirectAuthentication(Element crowdSettingsElement, Namespace namespace) {
        boolean defaultProxy = false;
        if (crowdSettingsElement.getChild(CrowdConfigKeys.DIRECT_AUTHENTICATION, namespace) != null) {
            defaultProxy = Boolean.parseBoolean(crowdSettingsElement
                    .getChild(CrowdConfigKeys.DIRECT_AUTHENTICATION, namespace).getValue());
        }
        crowdSettingsElement.removeChild(CrowdConfigKeys.DIRECT_AUTHENTICATION, namespace);
        log.info("Successfully removed " + CrowdConfigKeys.DIRECT_AUTHENTICATION);
        return defaultProxy;
    }

    private boolean moveOverrideAllGroupsUponLogin(Element crowdSettingsElement, Namespace namespace) {
        boolean defaultProxy = false;
        if (crowdSettingsElement.getChild(CrowdConfigKeys.OVERRIDE_ALL_GROUPS_UPON_LOGIN, namespace) != null) {
            defaultProxy = Boolean.parseBoolean(crowdSettingsElement
                    .getChild(CrowdConfigKeys.OVERRIDE_ALL_GROUPS_UPON_LOGIN, namespace).getValue());
        }
        crowdSettingsElement.removeChild(CrowdConfigKeys.OVERRIDE_ALL_GROUPS_UPON_LOGIN, namespace);
        log.info("Successfully removed " + CrowdConfigKeys.OVERRIDE_ALL_GROUPS_UPON_LOGIN);
        return defaultProxy;
    }

    private String moveApplicationName(Element crowdElement, Namespace namespace) {
        String applicationName = "";
        if (crowdElement.getChild(CrowdConfigKeys.APPLICATION_NAME, namespace) != null) {
            applicationName = crowdElement
                    .getChildText(CrowdConfigKeys.APPLICATION_NAME, namespace);
        }
        crowdElement.removeChild(CrowdConfigKeys.APPLICATION_NAME, namespace);
        log.info("Successfully removed " + CrowdConfigKeys.APPLICATION_NAME);
        return applicationName;
    }

    private String moveCustomCookieTokenKey(Element crowdElement, Namespace namespace) {
        String applicationName = "";
        if (crowdElement.getChild(CrowdConfigKeys.CUSTOM_COOKIE_TOKEN_KEY, namespace) != null) {
            applicationName = crowdElement
                    .getChildText(CrowdConfigKeys.CUSTOM_COOKIE_TOKEN_KEY, namespace);
        }
        crowdElement.removeChild(CrowdConfigKeys.CUSTOM_COOKIE_TOKEN_KEY, namespace);
        log.info("Successfully removed " + CrowdConfigKeys.CUSTOM_COOKIE_TOKEN_KEY);
        return applicationName;
    }

    private String moveServerUrl(Element crowdElement, Namespace namespace) {
        String serverUrl = "";
        if (crowdElement.getChild(CrowdConfigKeys.SERVER_URL, namespace) != null) {
            serverUrl = crowdElement
                    .getChildText(CrowdConfigKeys.SERVER_URL, namespace);
        }
        crowdElement.removeChild(CrowdConfigKeys.SERVER_URL, namespace);
        log.info("Successfully removed " + CrowdConfigKeys.SERVER_URL);
        return serverUrl;
    }

    private String movePassword(Element crowdElement, Namespace namespace) {
        String serverUrl = "";
        if (crowdElement.getChild(CrowdConfigKeys.PASSWORD, namespace) != null) {
            serverUrl = crowdElement
                    .getChildText(CrowdConfigKeys.PASSWORD, namespace);
        }
        crowdElement.removeChild(CrowdConfigKeys.PASSWORD, namespace);
        log.info("Successfully removed " + CrowdConfigKeys.PASSWORD);
        return serverUrl;
    }

    private long moveSessionValidationInterval(Element crowdSettingsElement, Namespace namespace) {
        long noAutoCreation = 5L;
        if (crowdSettingsElement.getChild(CrowdConfigKeys.SESSION_VALIDATION_INTERVAL, namespace) != null) {
            noAutoCreation = Long.parseLong(
                    crowdSettingsElement.getChild(CrowdConfigKeys.SESSION_VALIDATION_INTERVAL, namespace)
                            .getValue());
        }
        crowdSettingsElement.removeChild(CrowdConfigKeys.SESSION_VALIDATION_INTERVAL, namespace);
        log.info("Successfully removed " + CrowdConfigKeys.SESSION_VALIDATION_INTERVAL);
        return noAutoCreation;
    }

    @SneakyThrows
    private void removeCrowdRoot(Element security, Element crowdSettingsElement, Namespace namespace) {
        if (crowdSettingsElement.getChildren().isEmpty()) {
            security.removeChild(CROWD_SETTINGS, namespace);
            log.info("Successfully removed " + CROWD_SETTINGS);
        } else {
            String message = "Some elements still exist under node " + CROWD_SETTINGS
                    + " - can't delete it";
            log.error(message);
            throw new ConverterPreconditionException(message);
        }
    }
}
