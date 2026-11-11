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

package org.artifactory.convert;

import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.XmlUtils;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jfrog.common.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import java.io.IOException;
import java.io.InputStream;

import static org.artifactory.logging.converter.LogbackConverterUtils.ATTRIBUTE_CLASS;
import static org.artifactory.logging.converter.LogbackConverterUtils.ATTRIBUTE_NAME;
import static org.artifactory.logging.converter.LogbackConverterUtils.ELEMENT_TYPE_APPENDER;
import static org.artifactory.logging.converter.LogbackConverterUtils.ELEMENT_TYPE_LOGGER;

/**
 * Base class for the xml converters (version, security, metadata, etc.)
 *
 * @author Yossi Shaul
 */
public abstract class XmlConverterTest extends ArtifactoryHomeBoundTest {
    private static final Logger log = LoggerFactory.getLogger(XmlConverterTest.class);

    protected Document convertXml(String resourcePath, XmlConverter... converters) throws Exception {
        Document doc = getDocument(resourcePath);
        for (XmlConverter xmlConverter : converters) {
            xmlConverter.convert(doc);
        }
        return doc;
    }

    protected void debugContent(Document doc) {
        log.debug(XmlUtils.outputString(doc));
    }

    boolean elementExists(Element root, Namespace ns, String elementType, String elementName) {
        return root.getChildren(elementType, ns).stream()
                .anyMatch(appender -> appender.getAttributeValue(ATTRIBUTE_NAME, ns).equals(elementName));
    }

    boolean elementExistsByClass(Element root, Namespace ns, String elementType, String elementName) {
        return root.getChildren(elementType, ns).stream()
                .anyMatch(appender -> appender.getAttributeValue(ATTRIBUTE_CLASS, ns).equals(elementName));
    }

    String getLayoutInEncoder(Element root, Namespace ns, String elementName) {
        Element foundAppender = getElement(root, ns, ELEMENT_TYPE_APPENDER, elementName);
        Assert.assertNotNull(foundAppender, "Expected element" + elementName);
        return foundAppender.getChild("encoder").getChild("layout").getAttributeValue("class");
    }

    Element getFilterByClass(Element root, Namespace ns, String appenderName, String filterClass) {
        Element foundAppender = getElement(root, ns, ELEMENT_TYPE_APPENDER, appenderName);
        Assert.assertNotNull(foundAppender, "Expected element" + appenderName);
        return foundAppender.getChildren().stream()
                .filter(child -> child.getAttributeValue(ATTRIBUTE_CLASS).equals(filterClass)).findFirst().orElse(null);
    }

    boolean appenderRefExists(Element root, Namespace ns, String elementName, String refName) {
        Element foundLogger = getElement(root, ns, ELEMENT_TYPE_LOGGER, elementName);
        Assert.assertNotNull(foundLogger, "Expected element" + elementName);
        return foundLogger.getChildren().stream().anyMatch(curr -> curr.getAttributeValue("ref")
                .equals(refName));
    }

    protected Document getDocument(String resourcePath) throws IOException {
        try (InputStream is = ResourceUtils.getResource(resourcePath)) {
            return XmlUtils.parse(is);
        }
    }

    protected Element getElement(Element root, Namespace ns, String elementType, String elementName) {
        return root.getChildren(elementType, ns).stream().filter
                        (appender -> appender.getAttributeValue(ATTRIBUTE_NAME, ns).equals(elementName)).findFirst()
                .orElse(null);
    }
}
