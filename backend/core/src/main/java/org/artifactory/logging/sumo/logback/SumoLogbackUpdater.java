/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.logging.sumo.logback;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.artifactory.logging.sumo.SumoCategory;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.StringInputStream;
import org.artifactory.util.XmlUtils;
import org.jdom2.Attribute;
import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.LineSeparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Adds Sumo Logic appender to the logback.xml file
 *
 * @author Shay Yaakov
 */
@Component
public class SumoLogbackUpdater {
    private static final Logger log = LoggerFactory.getLogger(SumoLogbackUpdater.class);

    private static final String APPENDER_ELEMENT = "appender";
    private static final String SUMO_CONSOLE = "SUMO_CONSOLE";
    private static final String SUMO_ACCESS = "SUMO_ACCESS";
    private static final String SUMO_REQUEST = "SUMO_REQUEST";
    private static final String SUMO_TRAFFIC = "SUMO_TRAFFIC";

    private static final Map<String, Supplier<Element>> appenderElementFactories;

    static {
        appenderElementFactories = Map.of(
                SUMO_CONSOLE, SumoLogbackUpdater::getConsoleAppenderElement,
                SUMO_ACCESS, SumoLogbackUpdater::getAccessAppenderElement,
                SUMO_REQUEST, SumoLogbackUpdater::getRequestAppenderElement,
                SUMO_TRAFFIC, SumoLogbackUpdater::getTrafficAppenderElement
        );
    }

    private static final Map<String, String> loggerByAppender;

    static {
        loggerByAppender = Map.of(
                SUMO_CONSOLE, "root",
                SUMO_ACCESS, "org.artifactory.security.AccessLogger",
                SUMO_REQUEST, "org.artifactory.traffic.RequestLogger",
                SUMO_TRAFFIC, "org.artifactory.traffic.TrafficLogger"
        );
    }

    /**
     * Update logback.xml with sumologic appenders and refs if needed.
     * Also ensure sumo appenders are in the correct order - before the root element.
     */
    public void update(File logbackConfigFile, UpdateData updateData) throws IOException {
        Document doc = XmlUtils.parse(logbackConfigFile);
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        int rootSectionIndex = findInsertionIndex(root, ns);
        Element appender = findAppender(root, SUMO_CONSOLE);
        if (root.indexOf(appender) > rootSectionIndex) {
            removeSumoLogicAppenders(doc);
        }
        update(root, updateData, rootSectionIndex);
        Format format = getFormat();
        backupAndSaveLogback(logbackConfigFile, XmlUtils.outputString(format, doc));
    }

    /**
     * Return an index to insert sumo logic appenders before.
     */
    private int findInsertionIndex(Element root, Namespace ns) {
        List<Element> rootElement = root.getChildren("root", ns);
        if (CollectionUtils.isNullOrEmpty(rootElement)) {
            rootElement = root.getChildren(APPENDER_ELEMENT, ns);
        }
        return root.indexOf(rootElement.get(0)) - 1;
    }

    private static void update(Element root, UpdateData updateData, int rootSectionIndex) {
        try {
            addOrUpdateAppender(root, SUMO_REQUEST, updateData, rootSectionIndex);
            addOrUpdateAppender(root, SUMO_TRAFFIC, updateData, rootSectionIndex);
            addOrUpdateAppender(root, SUMO_ACCESS, updateData, rootSectionIndex);
            addOrUpdateAppender(root, SUMO_CONSOLE, updateData, rootSectionIndex);
        } catch (Exception e) {
            String err = "Error adding Sumo Logic appenders to logback.xml: ";
            log.error(err + e.getMessage());
            log.debug(err, e);
        }
    }

    private static void addOrUpdateAppender(Element root, String appenderName, UpdateData updateData,
            int rootSectionIndex) {
        Element appender = findAppender(root, appenderName);
        if (appender == null && updateData.getSumoConfig().isEnabled()) {
            appender = appenderElementFactories.get(appenderName).get();
            //Add before the root element
            root.addContent(rootSectionIndex + 1, appender);
            addAppenderRefToLogger(root, appenderName);
        }
        if (appender != null) {
            updateAppender(root, appender, updateData);
        }
    }

    public static void removeSumoLogicFromXml(File logbackXml) {
        Document xml = XmlUtils.parse(logbackXml);
        String oldContent = XmlUtils.outputString(xml);
        removeSumoLogicAppenders(xml);
        if (!oldContent.equals(XmlUtils.outputString(xml))) {
            saveXml(logbackXml, xml);
            log.warn("logback.xml configuration is invalid, cleaning sumologic data.");
        }
    }

    private static void removeSumoLogicAppenders(Document xml) {
        removeSumoLogicAppendersRefs(xml.getRootElement());
        removeSumoLogicAppenders(xml.getRootElement());
    }

    public static void verifyAndUpdateCollectorUrl(File logbackXml, String collectorUrl) {
        boolean[] requireChange = new boolean[1];
        Document xml = XmlUtils.parse(logbackXml);
        Element root = xml.getRootElement();
        root.getChildren(APPENDER_ELEMENT)
                .stream()
                .filter(element -> {
                    Attribute name = element.getAttribute("name");
                    return name != null && loggerByAppender.containsKey(name.getValue());
                })
                .forEach(element -> element.getChildren("collectorUrl").forEach(curl -> {
                    if (!curl.getText().equals(collectorUrl)) {
                        curl.setText(collectorUrl);
                        requireChange[0] = true;
                    }
                }));
        if (requireChange[0]) {
            saveXml(logbackXml, xml);
            log.warn(
                    "logback.xml is in invalid state, fixing collectors url in sumologic appenders to match the ones " +
                            "in the config descriptor.");
        }
    }

    private static void saveXml(File logbackXml, Document xml) {
        Format format = getFormat();

        try {
            backupAndSaveLogback(logbackXml, XmlUtils.outputString(format, xml));
        } catch (IOException e) {
            log.error("Attempt to remove SumoLogic data from logback.xml failed");
        }
    }

    private static Format getFormat() {
        Format format = Format.getPrettyFormat();
        format.setIndent("    ");
        format.setLineSeparator(LineSeparator.NL);
        return format;
    }

    private static void removeSumoLogicAppenders(Element root) {
        List<Content> toRemove = new ArrayList<>();
        root.getChildren(APPENDER_ELEMENT).forEach(appender -> {
            Attribute name = appender.getAttribute("name");
            if (name != null &&
                    loggerByAppender.keySet().stream().anyMatch(refTarget ->
                            name.getValue().equals(refTarget))) {
                toRemove.add(appender);
            }
        });
        toRemove.forEach(root::removeContent);
    }

    private static void removeSumoLogicAppendersRefs(Element root) {
        root.getChildren().forEach(element -> element.getChildren("appender-ref").forEach(appenderRef -> {
            Attribute ref = appenderRef.getAttribute("ref");
            if (ref != null &&
                    loggerByAppender.keySet().stream().anyMatch(refTarget -> ref.getValue().equals(refTarget))) {
                element.removeContent(appenderRef);
            }
        }));
    }

    private static Element findAppender(Element root, String appenderName) {
        Namespace ns = root.getNamespace();
        List<Element> appenders = root.getChildren(APPENDER_ELEMENT, ns);
        return appenders.stream()
                .filter(element -> element.getAttributeValue("name", ns).equals(appenderName))
                .findFirst().orElse(null);
    }

    private static void addAppenderRefToLogger(Element root, String appenderName) {
        Namespace ns = root.getNamespace();
        String loggerName = loggerByAppender.get(appenderName);
        if ("root".equals(loggerName)) {
            Element rootElement = root.getChild("root", ns);
            rootElement.addContent(getElement("<appender-ref ref=\"" + appenderName + "\"/>"));
        } else {
            List<Element> loggers = root.getChildren("logger", ns);
            loggers.stream()
                    .filter(logger -> StringUtils.equals(logger.getAttributeValue("name", ns), loggerName))
                    .forEach(logger -> logger.addContent(getElement("<appender-ref ref=\"" + appenderName + "\"/>")));
        }
    }

    private static void updateAppender(Element root, Element appender, UpdateData updateData) {
        SumoLogicConfigDescriptor sumoLogicConfig = updateData.getSumoConfig();
        boolean enabled = sumoLogicConfig.isEnabled() && StringUtils.isNotBlank(sumoLogicConfig.getCollectorUrl());
        updateAppenderStringProperty(root, appender, "enabled", () -> String.valueOf(enabled));
        updateAppenderStringProperty(root, appender, "collectorUrl", sumoLogicConfig::getCollectorUrl);
        updateAppenderProxyProperty(root, appender, () -> proxyToXmlElement(sumoLogicConfig.getProxy()));
        updateAppenderStringProperty(root, appender, "artifactoryHost", updateData::getArtifactoryHost);
        updateAppenderStringProperty(root, appender, "artifactoryNode", updateData::getArtifactoryNode);
    }

    private static void updateAppenderStringProperty(Element root, Element appender, String propName,
            Supplier<String> valueSupplier) {
        String value = valueSupplier.get();
        Namespace ns = root.getNamespace();
        Element child = appender.getChild(propName, ns);
        if (child == null) {
            if (value == null) {
                return;
            }
            child = getElement("<" + propName + "/>");
            appender.addContent(child);
        } else if (value == null) {
            appender.removeContent(child);
            return;
        }
        child.setText(value);
    }

    private static void updateAppenderProxyProperty(Element root, Element appender,
            Supplier<Content> valueSupplier) {
        Namespace ns = root.getNamespace();
        Element child = appender.getChild("proxy", ns);
        if (child != null) {
            appender.removeContent(child);
        }
        Content value = valueSupplier.get();
        if (value != null) {
            appender.addContent(value);
        }
    }

    private static Element proxyToXmlElement(ProxyDescriptor proxy) {
        if (proxy == null) {
            return null;
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(ProxyDescriptor.class);
            JAXBElement<ProxyDescriptor> jaxbElement = new JAXBElement<>(new QName("temp-ns", "proxy"),
                    ProxyDescriptor.class, proxy);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            marshaller.marshal(jaxbElement, out);
            String xml = out.toString("utf-8");
            //Remove jaxb decorations (namespace, attributes, etc.)
            xml = xml.replaceFirst("<[^/>]*proxy[^>]*>", "<proxy>");
            xml = xml.replaceFirst("</[^>]*proxy[^>]*>", "</proxy>");
            return getElement(xml);
        } catch (JAXBException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Element getConsoleAppenderElement() {
        return getSumoAppenderTemplate(SUMO_CONSOLE, SumoCategory.CONSOLE,
                "org.artifactory.logging.layout.BackTracePatternLayout",
                "%date [%thread] [%-5level] \\(%-20c{3}:%L\\) %message%n");
    }

    private static Element getAccessAppenderElement() {
        return getSumoAppenderTemplate(SUMO_ACCESS, SumoCategory.ACCESS, null, "%date %message%n");
    }

    private static Element getRequestAppenderElement() {
        return getSumoAppenderTemplate(SUMO_REQUEST, SumoCategory.REQUEST, null, "%message%n");
    }

    private static Element getTrafficAppenderElement() {
        return getSumoAppenderTemplate(SUMO_TRAFFIC, SumoCategory.TRAFFIC, null, "%message%n");
    }

    private static Element getSumoAppenderTemplate(String name, SumoCategory category, String layoutClass,
            String pattern) {
        String layoutClassAttribute = layoutClass == null ? "" : " class=\"" + layoutClass + "\"";
        String appender =
                "<appender name=\"" + name + "\" class=\"org.artifactory.logging.sumo.SumoAppender\">" +
                        "    <layout" + layoutClassAttribute + ">" +
                        "        <pattern>" + pattern + "</pattern>" +
                        "    </layout>" +
                        "    <category>" + category.getName() + "</category>" +
                        "</appender>";
        return getElement(appender);
    }

    private static Element getElement(String input) {
        SAXBuilder builder = XmlUtils.createSaxBuilder();
        try (InputStream stream = new StringInputStream(input)) {
            Document doc = builder.build(stream);
            Element root = doc.getRootElement();
            return root.detach();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a backup of the existing logback configuration file and proceeds to save the given content
     *
     * @param content The converter logback.xml content
     */
    private static void backupAndSaveLogback(File originalFile, String content) throws IOException {
        File parentDir = originalFile.getParentFile();
        File logbackConfigFile = new File(parentDir, originalFile.getName());
        File originalBackup = new File(parentDir, "logback.original.xml");
        if (originalBackup.exists()) {
            FileUtils.deleteQuietly(originalBackup);
        }
        FileUtils.moveFile(logbackConfigFile, originalBackup);
        FileUtils.writeStringToFile(logbackConfigFile, content, "utf-8");
    }

    public static class UpdateData {
        private final SumoLogicConfigDescriptor sumoConfig;
        private final String artifactoryHost;
        private final String artifactoryNode;

        public UpdateData(SumoLogicConfigDescriptor sumoConfig, String artifactoryHost, String artifactoryNode) {
            this.sumoConfig = Objects.requireNonNull(sumoConfig, "SumoLogic config descriptor is required");
            this.artifactoryHost = Objects.requireNonNull(artifactoryHost, "Artifactory host is required");
            this.artifactoryNode = artifactoryNode;
        }

        public SumoLogicConfigDescriptor getSumoConfig() {
            return sumoConfig;
        }

        public String getArtifactoryHost() {
            return artifactoryHost;
        }

        public String getArtifactoryNode() {
            return artifactoryNode;
        }
    }
}
