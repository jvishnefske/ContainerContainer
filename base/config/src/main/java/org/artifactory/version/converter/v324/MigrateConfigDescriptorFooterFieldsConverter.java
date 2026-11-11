package org.artifactory.version.converter.v324;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.platform.PlatformConfigHolder;
import org.artifactory.platform.PlatformGeneralConfig;
import org.artifactory.platform.PlatformSystemMessageConfig;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.platform.PlatformConfigKeys.DATE_FORMAT;
import static org.artifactory.platform.PlatformConfigKeys.HELP_LINKS_ENABLED;
import static org.artifactory.platform.PlatformConfigKeys.LOGO;
import static org.artifactory.platform.PlatformConfigKeys.SYSTEM_MESSAGE;
import static org.artifactory.platform.PlatformConfigKeys.SYSTEM_MESSAGE_CONFIG;
import static org.artifactory.platform.PlatformConfigKeys.SYSTEM_MESSAGE_ENABLED;
import static org.artifactory.platform.PlatformConfigKeys.SYSTEM_MESSAGE_TITLE;
import static org.artifactory.platform.PlatformConfigKeys.SYSTEM_MESSAGE_TITLE_COLOR;

/**
 * @author Andrey Tashlitsky
 */
@Slf4j
public class MigrateConfigDescriptorFooterFieldsConverter implements XmlConverter {

    private static final String SUCCESSFUL_LOG = "Successfully removed {} from config descriptor";

    @Override
    public void convert(Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        migrateGeneralElements(doc.getRootElement(), namespace);
        migrateSystemMessageElement(doc.getRootElement(), namespace);
    }

    private void migrateGeneralElements(Element element, Namespace namespace) {
        PlatformGeneralConfig.PlatformGeneralConfigBuilder builder =
                PlatformGeneralConfig.builder();
        Element dateFormatElement = element.getChild(DATE_FORMAT, namespace);
        if (dateFormatElement != null) {
            builder.dateFormat(dateFormatElement.getValue());
            element.removeChild(DATE_FORMAT, namespace);
            log.info(SUCCESSFUL_LOG, DATE_FORMAT);
        }
        Element helpLinksEnabledElement = element.getChild(HELP_LINKS_ENABLED, namespace);
        if (helpLinksEnabledElement != null) {
            builder.helpLinksEnabled(Boolean.parseBoolean(helpLinksEnabledElement.getValue()));
            element.removeChild(HELP_LINKS_ENABLED, namespace);
            log.info(SUCCESSFUL_LOG, HELP_LINKS_ENABLED);
        }
        Element logoElement = element.getChild(LOGO, namespace);
        if (logoElement != null) {
            builder.logo(logoElement.getValue());
            element.removeChild(LOGO, namespace);
            log.info(SUCCESSFUL_LOG, LOGO);
        }
        PlatformConfigHolder.getInstance().addPlatformGeneralConfig(builder.build());
    }

    private void migrateSystemMessageElement(Element element, Namespace namespace) {
        Element systemMessageElement = element.getChild(SYSTEM_MESSAGE_CONFIG, namespace);
        if (systemMessageElement != null) {
            PlatformSystemMessageConfig.PlatformSystemMessageConfigBuilder builder =
                    PlatformSystemMessageConfig.builder();
            Element enabledElement = systemMessageElement.getChild(SYSTEM_MESSAGE_ENABLED, namespace);
            if (enabledElement != null) {
                builder.systemMessageEnabled(Boolean.parseBoolean(enabledElement.getValue()));
            }
            Element titleElement = systemMessageElement.getChild(SYSTEM_MESSAGE_TITLE, namespace);
            if (titleElement != null) {
                builder.systemMessageTitle(titleElement.getValue());
            }
            Element titleColorElement = systemMessageElement.getChild(SYSTEM_MESSAGE_TITLE_COLOR, namespace);
            if (titleColorElement != null) {
                builder.systemMessageTitleColor(titleColorElement.getValue());
            }
            Element messageElement = systemMessageElement.getChild(SYSTEM_MESSAGE, namespace);
            if (messageElement != null) {
                builder.systemMessage(messageElement.getValue());
            }
            element.removeChild(SYSTEM_MESSAGE_CONFIG, namespace);
            log.info(SUCCESSFUL_LOG, SYSTEM_MESSAGE_CONFIG);
            PlatformConfigHolder.getInstance().addPlatformSystemMessageConfig(builder.build());
        }
    }
}
