package org.artifactory.version.converter.v325;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.config.AccessConfigKeys.HttpSsoConfigKeys;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jfrog.access.client.model.HttpSsoSetting;

import static org.artifactory.config.AccessConfigKeys.HTTP_SSO_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.SECURITY;

@Slf4j
public class MigrateSsoHttpToConfigServiceConverter implements XmlConverter {

    @Override
    public void convert(Document doc) {
        removeElements(doc);
    }

    private void removeElements(Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element security = rootElement.getChild(SECURITY, namespace);
        if (security != null) {
            Element httpSsoElement = security.getChild(HTTP_SSO_SETTINGS, namespace);
            if (httpSsoElement != null) {
                boolean httpSsoProxied = moveHttpSsoProxied(httpSsoElement, namespace);
                boolean noAutoCreation = moveNoAutoCreation(httpSsoElement, namespace);
                boolean allowUserToAccessProfile = moveAllowUserToAccessProfile(httpSsoElement, namespace);
                String remoteUserRequestVariable = moveRemoteUserRequestVariable(httpSsoElement, namespace);
                boolean syncLdapGroup = moveSyncLdapGroups(httpSsoElement, namespace);
                removeHttpSsoRoot(security, httpSsoElement, namespace);
                HttpSsoSetting httpSsoSetting = HttpSsoSetting
                        .builder()
                        .httpSsoProxied(httpSsoProxied)
                        .allowUserToAccessProfile(allowUserToAccessProfile)
                        .autoCreateUser(!noAutoCreation)
                        .remoteUserRequestVariable(remoteUserRequestVariable)
                        .syncLdapGroups(syncLdapGroup)
                        .build();
                AccessConfigHolder.getInstance().setHttpSsoSetting(httpSsoSetting);
            }
        }
    }

    private boolean moveHttpSsoProxied(Element httpSsoElement, Namespace namespace) {
        boolean httpSsoProxied = false;
        if (httpSsoElement.getChild(HttpSsoConfigKeys.HTTP_SSO_PROXIED, namespace) != null) {
            httpSsoProxied = Boolean.parseBoolean(
                    httpSsoElement.getChild(HttpSsoConfigKeys.HTTP_SSO_PROXIED, namespace).getValue());
        }
        httpSsoElement.removeChild(HttpSsoConfigKeys.HTTP_SSO_PROXIED, namespace);
        log.info("Successfully removed " + HttpSsoConfigKeys.HTTP_SSO_PROXIED);
        return httpSsoProxied;
    }

    private boolean moveNoAutoCreation(Element httpSsoElement, Namespace namespace) {
        boolean noAutoCreation = false;
        if (httpSsoElement.getChild(HttpSsoConfigKeys.NO_AUTO_CREATION, namespace) != null) {
            noAutoCreation = Boolean.parseBoolean(
                    httpSsoElement.getChild(HttpSsoConfigKeys.NO_AUTO_CREATION, namespace).getValue());
        }
        httpSsoElement.removeChild(HttpSsoConfigKeys.NO_AUTO_CREATION, namespace);
        log.info("Successfully removed " + HttpSsoConfigKeys.NO_AUTO_CREATION);
        return noAutoCreation;
    }
    private boolean moveAllowUserToAccessProfile(Element httpSsoElement, Namespace namespace) {
        boolean allowUserToAccessProfile = false;
        if (httpSsoElement.getChild(HttpSsoConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE, namespace) != null) {
            allowUserToAccessProfile = Boolean.parseBoolean(httpSsoElement
                    .getChild(HttpSsoConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE, namespace).getValue());
        }
        httpSsoElement.removeChild(HttpSsoConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE, namespace);
        log.info("Successfully removed " + HttpSsoConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE);
        return allowUserToAccessProfile;
    }

    private String moveRemoteUserRequestVariable(Element httpSsoElement, Namespace namespace) {
        String remoteUserRequestVariable = "REMOTE_USER";
        if (httpSsoElement.getChild(HttpSsoConfigKeys.REMOTE_USER_REQUEST_VARIABLE, namespace) != null) {
            remoteUserRequestVariable = httpSsoElement
                    .getChildText(HttpSsoConfigKeys.REMOTE_USER_REQUEST_VARIABLE, namespace);
        }
        httpSsoElement.removeChild(HttpSsoConfigKeys.REMOTE_USER_REQUEST_VARIABLE, namespace);
        log.info("Successfully removed " + HttpSsoConfigKeys.REMOTE_USER_REQUEST_VARIABLE);
        return remoteUserRequestVariable;
    }

    private boolean moveSyncLdapGroups(Element httpSsoElement, Namespace namespace) {
        boolean noAutoCreation = false;
        if (httpSsoElement.getChild(HttpSsoConfigKeys.SYNC_LDAP_GROUPS, namespace) != null) {
            noAutoCreation = Boolean.parseBoolean(
                    httpSsoElement.getChild(HttpSsoConfigKeys.SYNC_LDAP_GROUPS, namespace).getValue());
        }
        httpSsoElement.removeChild(HttpSsoConfigKeys.SYNC_LDAP_GROUPS, namespace);
        log.info("Successfully removed " + HttpSsoConfigKeys.SYNC_LDAP_GROUPS);
        return noAutoCreation;
    }

    @SneakyThrows
    private void removeHttpSsoRoot(Element security, Element httpSsoElement, Namespace namespace) {
        if (httpSsoElement.getChildren().isEmpty()) {
            security.removeChild(HTTP_SSO_SETTINGS, namespace);
            log.info("Successfully removed " + HTTP_SSO_SETTINGS);
        } else {
            String message = "Some elements still exist under node " + HTTP_SSO_SETTINGS
                    + " - can't delete it";
            log.error(message);
            throw new ConverterPreconditionException(message);
        }
    }
}