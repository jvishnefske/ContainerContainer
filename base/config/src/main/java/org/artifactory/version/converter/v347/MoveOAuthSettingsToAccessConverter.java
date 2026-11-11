package org.artifactory.version.converter.v347;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.access.client.model.OAuthSetting;

import java.util.ArrayList;
import java.util.List;

import static org.artifactory.config.AccessConfigKeys.OAUTH_PROVIDER_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.OAUTH_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.SECURITY;

@Slf4j
public class MoveOAuthSettingsToAccessConverter implements XmlConverter {

    @Override
    public void convert(Document doc) {
        removeElements(doc);
    }

    private void removeElements(Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element security = rootElement.getChild(SECURITY, namespace);
        if (security != null) {
            processOAuthSettings(security, namespace);
            security.removeChild(OAUTH_SETTINGS, namespace);
            log.info("Successfully removed " + OAUTH_SETTINGS);
        }
    }

    private void processOAuthSettings(Element security, Namespace namespace) {
        Element oAuthSettingsParent = security.getChild(OAUTH_SETTINGS, namespace);
        if (oAuthSettingsParent != null) {
            OAuthGeneralSettings oAuthGeneralSettings = parseOAuthGeneralSettings(oAuthSettingsParent, namespace);
            MovedFields moved = parseMovedSettings(oAuthSettingsParent, namespace);
            AccessConfigHolder.getInstance().addOAuthGeneralSettings(oAuthGeneralSettings);
            processOAuthProviders(oAuthSettingsParent, namespace, moved);
        }
    }

    private void processOAuthProviders(Element oAuthSettingsParent, Namespace namespace, MovedFields moved) {
        Element oAuthProvidersSettings = oAuthSettingsParent.getChild(OAUTH_PROVIDER_SETTINGS, namespace);
        if (oAuthProvidersSettings != null) {
            List<Element> oAuthProviderSettingsList =
                    oAuthProvidersSettings.getChildren(OAUTH_PROVIDER_SETTINGS, namespace);
            if (oAuthProviderSettingsList != null && !oAuthProviderSettingsList.isEmpty()) {
                List<OAuthSetting> accessOAuthSettingsList = new ArrayList<>();
                for (Element oAuthProviderSetting : oAuthProviderSettingsList) {
                    accessOAuthSettingsList.add(parseOAuthProviderSetting(oAuthProviderSetting, namespace, moved));
                }
                AccessConfigHolder.getInstance().addOAuthSetting(accessOAuthSettingsList);
            }
        }
    }

    private MovedFields parseMovedSettings(Element element, Namespace ns) {
        Element allowUserToAccessProfile = element.getChild("allowUserToAccessProfile", ns);
        Element persistUsers = element.getChild("persistUsers", ns);
        return new MovedFields(
                allowUserToAccessProfile != null && Boolean.parseBoolean(allowUserToAccessProfile.getValue()),
                persistUsers != null && Boolean.parseBoolean(persistUsers.getValue()));
    }

    private OAuthGeneralSettings parseOAuthGeneralSettings(Element element, Namespace ns) {
        OAuthGeneralSettings.OAuthGeneralSettingsBuilder builder = OAuthGeneralSettings.builder();

        Element enable = element.getChild("enableIntegration", ns);
        builder.enable(enable != null && Boolean.parseBoolean(enable.getValue()));

        Element defaultSetting = element.getChild("defaultNpm", ns);
        builder.defaultSetting(
                defaultSetting != null && defaultSetting.getText() != null ? defaultSetting.getText() : null);

        return builder.build();
    }

    private OAuthSetting parseOAuthProviderSetting(Element element, Namespace ns, MovedFields moved) {

        OAuthSetting.OAuthSettingBuilder builder = OAuthSetting.builder();

        Element enabled = element.getChild("enabled", ns);
        builder.enabled(enabled != null && enabled.getValue() != null && Boolean.parseBoolean(enabled.getValue()));

        Element central = element.getChild("central", ns);
        builder.central(central != null && central.getValue() != null && Boolean.parseBoolean(central.getValue()));

        Element pkce = element.getChild("pkce", ns);
        builder.pkce(pkce != null && pkce.getValue() != null && Boolean.parseBoolean(pkce.getValue()));

        Element useDefaultProxy = element.getChild("useDefaultProxy", ns);
        builder.useDefaultProxy(useDefaultProxy != null && useDefaultProxy.getValue() != null &&
                Boolean.parseBoolean(useDefaultProxy.getValue()));

        builder.name(getTextValue(element, "name", ns));
        builder.providerType(getTextValue(element, "providerType", ns));
        builder.clientId(getTextValue(element, "id", ns));
        builder.clientSecret(getTextValue(element, "secret", ns));
        builder.apiUrl(getTextValue(element, "apiUrl", ns));
        builder.authUrl(getTextValue(element, "authUrl", ns));
        builder.tokenUrl(getTextValue(element, "tokenUrl", ns));
        builder.basicUrl(getTextValue(element, "basicUrl", ns));
        builder.domain(getTextValue(element, "domain", ns));

        //Configs Moved from General to Provider
        builder.allowUserToAccessProfile(moved.allowUserToAccessProfile());
        builder.autoUserCreation(moved.persistUsers());

        return builder.build();
    }

    private String getTextValue(Element element, String name, Namespace ns) {
        Element child = element.getChild(name, ns);
        return child != null && child.getText() != null ? child.getText() : null;
    }

    private record MovedFields(boolean allowUserToAccessProfile, boolean persistUsers) {
    }
}
