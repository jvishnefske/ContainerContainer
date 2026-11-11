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

package org.artifactory.util;

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.exception.DocumentBuildException;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author yoavl
 */
@Slf4j
@UtilityClass
public final class XmlUtils {
    private static final String DOM_ERROR_MESSAGE = "Failed to build dom document";
    private static final String VALIDATION_FEATURE = "http://xml.org/sax/features/validation";
    private static final String DISALLOW_DOCTYPE_DECL_FEATURE = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String LOAD_DTD_GRAMMAR_FEATURE =
            "http://apache.org/xml/features/nonvalidating/load-dtd-grammar";
    private static final String LOAD_EXTERNAL_DTD_FEATURE =
            "http://apache.org/xml/features/nonvalidating/load-external-dtd";
    private static final String EXTERNAL_GENERAL_ENTITIES_FEATURE =
            "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES_FEATURE =
            "http://xml.org/sax/features/external-parameter-entities";
    private static final List<String> featuresToDisable = List.of(
            VALIDATION_FEATURE,
            LOAD_DTD_GRAMMAR_FEATURE,
            LOAD_EXTERNAL_DTD_FEATURE,
            EXTERNAL_GENERAL_ENTITIES_FEATURE,
            EXTERNAL_PARAMETER_ENTITIES_FEATURE);

    public static String outputString(Document doc) {
        return new XMLOutputter().outputString(doc);
    }

    public static String outputString(Format format, Document doc) {
        return new XMLOutputter(format).outputString(doc);
    }

    public static Document parse(String xmlContent) {
        SAXBuilder sb = createSaxBuilder();
        try {
            return sb.build(new StringReader(xmlContent));
        } catch (Exception e) {
            throw new DocumentBuildException(e.getMessage(), e);
        }
    }

    public static Document parseStrict(String xmlContent) {
        SAXBuilder sb = createStrictSaxBuilder();
        try {
            return sb.build(new StringReader(xmlContent));
        } catch (Exception e) {
            throw new DocumentBuildException(e.getMessage(), e);
        }
    }

    public static Document parse(File input) {
        SAXBuilder sb = createSaxBuilder();
        try {
            return sb.build(new BufferedReader(
                    new InputStreamReader(new FileInputStream(input), StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new DocumentBuildException(DOM_ERROR_MESSAGE, e);
        }
    }

    public static Document parse(InputStream in) {
        SAXBuilder sb = createSaxBuilder();
        try {
            return sb.build(in);
        } catch (Exception e) {
            throw new DocumentBuildException(DOM_ERROR_MESSAGE, e);
        }
    }

    public static SAXBuilder createSaxBuilder() {
        return createSaxBuilder(false);
    }

    public static SAXBuilder createStrictSaxBuilder() {
        return createSaxBuilder(true);
    }

    public static XMLReader createStrictXmlReader() throws JDOMException, SAXException {
        return createXmlReader(true);
    }

    public static XMLReader createXmlReader() throws JDOMException, SAXException {
        return createXmlReader(false);
    }

    public static XMLReader createXmlReader(boolean strict)
            throws JDOMException, SAXException {
        SAXBuilder sb = createSaxBuilder(false);
        XMLReader reader = sb.getXMLReaderFactory().createXMLReader();
        for (String feature : featuresToDisable) {
            reader.setFeature(feature, false);
        }
        if (strict) {
            reader.setFeature(DISALLOW_DOCTYPE_DECL_FEATURE, true);
        }
        return reader;
    }

    /**
     * Attempts to parse the XML content from the provided Reader.
     * If parsing fails, it returns null instead of throwing an exception.
     *
     * @param reader the Reader containing XML content
     * @return a Document if parsing is successful, null otherwise
     */
    @Nullable
    public static Document canParseXml(Reader reader) {
        try {
            String xmlContent = readXml(reader);
            Document parsedocument;
            parsedocument = org.jfrog.common.XmlUtils.parse(xmlContent);
            return parsedocument;
        } catch (Exception e) {
            log.debug("Failed to parse XML content. Continue to filter Resource", e);
            return null;
        }
    }

    /**
     * Override the settings.xml configuration with the OIDC token placeholder
     * @return a new reader with the modified XML content - the username and password sections are removed and the
     * configuration section is added under the server section
     * @throws IOException if an error occurs while reading the reader
     */
    public static Reader overrideSettingConfiguration(Reader reader, Document parsedocument,
            String generatedSettingsUserCredentials)
            throws IOException {

        Document document = removeNamespaces(parsedocument);

        Element rootElement = document.getRootElement();
        Element serversElement = rootElement.getChild("servers");
        if (serversElement != null) {
            updateServersElement(serversElement, generatedSettingsUserCredentials);

            StringWriter documentWriter = new StringWriter();
            XMLOutputter xmlOutputter = new XMLOutputter(Format.getPrettyFormat());
            xmlOutputter.output(document, documentWriter);

            return new StringReader(documentWriter.toString());
        }
        return reader;
    }

    private void updateServersElement(Element serversElement , String generatedSettingsUserCredentials) {
        List<Element> serverElementList = serversElement.getChildren();

        String tokenStringToInject = """ 
                Bearer %s
                """.formatted(generatedSettingsUserCredentials);

        for (Element serverElement : serverElementList) {
            Element usernameElement = serverElement.getChild("username");
            Element passwordElement = serverElement.getChild("password");

            if (usernameElement != null && passwordElement != null) {
                serverElement.removeContent(usernameElement);
                serverElement.removeContent(passwordElement);
                setConfigurationElement(serverElement, tokenStringToInject);
            }
        }
    }

    private String readXml(Reader reader) throws IOException {
        StringWriter stringWriter = new StringWriter();
        BufferedReader bufferedReader = new BufferedReader(reader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            stringWriter.write(line + "\n");
        }

        return stringWriter.toString();
    }

    private void setConfigurationElement(Element serverElement, String tokenString) {
        Element configurationElement = new Element("configuration");
        Element httpHeadersElement = new Element("httpHeaders");
        Element propertyElement = new Element("property");
        Element nameElement = new Element("name").setText("Authorization");
        Element valueElement = new Element("value").setText(tokenString);

        propertyElement.addContent(nameElement);
        propertyElement.addContent(valueElement);
        httpHeadersElement.addContent(propertyElement);
        configurationElement.addContent(httpHeadersElement);
        serverElement.addContent(configurationElement);
    }

    private Document removeNamespaces(Document document) {
        removeNamespaces(document.getRootElement());
        return document;
    }

    private void removeNamespaces(Element element) {
        element.setNamespace(null);
        for (Element child : element.getChildren()) {
            removeNamespaces(child);
        }
    }

    private static SAXBuilder createSaxBuilder(boolean strict) {
        SAXBuilder sb = new SAXBuilder();
        //don't validate and don't load dtd
        sb.setXMLReaderFactory(XMLReaders.NONVALIDATING);
        for (String feature : featuresToDisable) {
            sb.setFeature(feature, false);
        }
        // JDK 24+ defaults disallow-doctype-decl to true (JDK-8284882); set explicitly for both modes
        sb.setFeature(DISALLOW_DOCTYPE_DECL_FEATURE, strict);
        return sb;
    }
}