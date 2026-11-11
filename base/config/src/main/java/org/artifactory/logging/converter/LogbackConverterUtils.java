/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.logging.converter;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.util.StringInputStream;
import org.artifactory.util.XmlUtils;
import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.Text;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jfrog.common.BiOptional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * @author Shay Bagants
 * @author Dan Feldman
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LogbackConverterUtils {
    private static final Logger log = LoggerFactory.getLogger(LogbackConverterUtils.class);

    public static final String ELEMENT_TYPE_APPENDER = "appender";
    public static final String ELEMENT_TYPE_LOGGER = "logger";
    public static final String ELEMENT_TYPE_PROPERTY = "property";
    public static final String ELEMENT_TYPE_TURBO_FILTER = "turboFilter";
    public static final String ATTRIBUTE_CLASS = "class";
    public static final String ATTRIBUTE_FILTER = "filter";
    public static final String ATTRIBUTE_NAME = "name";
    private static final String ATTRIBUTE_ENCODER = "encoder";
    private static final String ATTRIBUTE_LAYOUT = "layout";
    private static final String ATTRIBUTE_PATTERN = "pattern";

    /**
     * Appends {@param appenderContent} with {@param appenderName} to the end of the appenders list in the xml
     */
    public static void addAppender(Element root, Namespace ns, String appenderName, String appenderContent) {
        addElement(root, ns, ELEMENT_TYPE_APPENDER, appenderName, appenderContent);
    }

    /**
     * Appends {@param appender} with {@param appenderName} to the end of the appenders list in the xml
     */
    public static void addAppender(Element root, Namespace ns, String appenderName, Element appender) {
        String appenderContent = getContent(appender);
        addElement(root, ns, ELEMENT_TYPE_APPENDER, appenderName, appenderContent);
    }

    /**
     * Appends {@param turboFilterName} with {@param turboFilterName} to the end of  the xml
     */
    public static void addTurboFilter(Element root, Namespace ns, String turboFilterName, String turboFilterContent) {
        List<Element> elements = root.getChildren(ELEMENT_TYPE_TURBO_FILTER, ns);
        performIfElementNotPresentElement(root, ns, ELEMENT_TYPE_TURBO_FILTER,
                elementClassEquals(ns, turboFilterName),
                element -> log
                        .info("{} {} already exists in logback.xml, skipping conversion",
                                ELEMENT_TYPE_TURBO_FILTER, turboFilterName),
                () -> appendElementContent(root, turboFilterName, turboFilterContent, elements)
        );
    }

    /**
     * Appends {@param propertyName} with {@param propertyValue} to the end of  the xml
     */
    public static void addProperty(Element root, Namespace ns, String propertyName, String propertyValue) {
        addElement(root, ns, ELEMENT_TYPE_PROPERTY, propertyName, propertyValue);
    }

    /**
     * Removes {@param propertyName} if it exists in the list of properties in this {@param root} element.
     */
    public static Optional<Element> removeProperty(Element root, Namespace ns, String propertyName) {
        return performForElement(root, ns, ELEMENT_TYPE_PROPERTY,
                elementNameEquals(ns, propertyName),
                element -> {
                    log.info("removing property: {}", propertyName);
                    root.removeContent(element);
                }
        );
    }

    /**
     * Updates the value attribute of an existing property in place, or adds it if it doesn't exist.
     * This method preserves the property's position in the XML file.
     */
    public static void updateProperty(Element root, Namespace ns, String propertyName, String newValue) {
        findProperty(root, ns, propertyName).ifPresentOrElse(
                property -> {
                    property.setAttribute("value", newValue);
                    log.info("Updated property '{}' to value: {}", propertyName, newValue);
                },
                () -> {
                    String propertyContent = String.format("<property name=\"%s\" value=\"%s\"/>",
                            propertyName, newValue);
                    addProperty(root, ns, propertyName, propertyContent);
                    log.info("Added new property '{}' with value: {}", propertyName, newValue);
                }
        );
    }

    /**
     * Finds a property element by name.
     */
    public static Optional<Element> findProperty(Element root, Namespace ns, String propertyName) {
        return root.getChildren(ELEMENT_TYPE_PROPERTY, ns).stream()
                .filter(elementNameEquals(ns, propertyName))
                .findFirst();
    }

    /**
     * Add filter to an existing appender
     */
    public static void addFilterToAppender(Element root, Namespace ns, String appenderName, String filterContent) {
        performForElement(root, ns, ELEMENT_TYPE_APPENDER,
                elementNameEquals(ns, appenderName),
                element -> {
                    try {
                        Element filter = getElement(filterContent);
                        boolean exists =
                                element.getContent().stream().anyMatch(content -> filterEquals(filter, content));
                        if (!exists) {
                            log.info("Add filter {} to appender: {}", filter.getName(), appenderName);
                            element.getContent().add(1, filter);
                        }
                    } catch (IOException | JDOMException e) {
                        log.error("Failed to add filter to {} appender", appenderName, e);
                    }
                });
    }

    /**
     * Replace filter class in appender with {@param appenderName}
     */
    public static void replaceAppenderFilterClass(Element root, Namespace ns, String appenderName,
            String currentFilterClass, String newFilterClass) {
        performForElement(root, ns, ELEMENT_TYPE_APPENDER,
                elementNameEquals(ns, appenderName),
                element -> {
                    Optional<Element> filter =
                            element.getChildren().stream().filter(child -> child.getName().equals(ATTRIBUTE_FILTER) &&
                                    child.getAttributeValue(ATTRIBUTE_CLASS).equals(currentFilterClass)).findFirst();
                    if (filter.isPresent() &&
                            filter.get().getAttributeValue(ATTRIBUTE_CLASS).equals(currentFilterClass)) {
                        filter.get().setAttribute(ATTRIBUTE_CLASS, newFilterClass);
                        log.info("Replaced filter class in appender {}, from [{}] to [{}]", appenderName,
                                currentFilterClass, newFilterClass);
                    }
                });
    }


    /**
     * Replace encoder pattern class in appender with {@param appenderName}
     */
    public static void replaceAppenderEncoderPattern(Element root, Namespace ns, String appenderName,
            String currentPattern, String newPattern) {
        performForElement(root, ns, ELEMENT_TYPE_APPENDER,
                elementNameEquals(ns, appenderName),
                element -> Optional.ofNullable(element.getChild(ATTRIBUTE_ENCODER))
                        .map(encoder -> encoder.getChild(ATTRIBUTE_LAYOUT))
                        .map(layout -> layout.getChild(ATTRIBUTE_PATTERN))
                        .filter(pattern -> pattern.getValue().equals(currentPattern))
                        .ifPresent(pattern -> {
                            pattern.setText(newPattern);
                            log.info("Replaced encoder pattern in appender {}, from [{}] to [{}]", appenderName,
                                    currentPattern, newPattern);
                        }));
    }

    private static boolean filterEquals(Element filter, Content other) {
        if (other instanceof Element element) {
            return filter.getName().equals(element.getName()) &&
                    filter.getAttributeValue(ATTRIBUTE_CLASS).equals(element.getAttributeValue(ATTRIBUTE_CLASS));
        }
        return false;
    }

    /**
     * Appends {@param loggerContent} with {@param loggerName} to the end of the loggers list in the xml
     */
    public static void addLogger(Element root, Namespace ns, String loggerName, String loggerContent) {
        addElement(root, ns, ELEMENT_TYPE_LOGGER, loggerName, loggerContent);
    }

    /**
     * Appends {@param logger} with {@param loggerName} to the end of the loggers list in the xml
     */
    public static void addLogger(Element root, Namespace ns, String loggerName, Element logger) {
        String loggerContent = getContent(logger);
        addElement(root, ns, ELEMENT_TYPE_LOGGER, loggerName, loggerContent);
    }

    private static String getContent(Element element) {
        XMLOutputter xmlOutputter = new XMLOutputter(Format.getPrettyFormat());
        return xmlOutputter.outputString(element);
    }

    /**
     * Appends {@param elementContent} with {@param elementName} to the end of the loggers list in the xml
     */
    private static void addElement(Element root, Namespace ns, String elementType, String elementName,
            String elementContent) {
        List<Element> elements = root.getChildren(elementType, ns);
        performIfElementNotPresentElement(root, ns, elementType,
                elementNameEquals(ns, elementName),
                element -> log
                        .info("{} {} already exists in logback.xml, skipping conversion", elementType, elementName),
                () -> appendElementContent(root, elementName, elementContent, elements)
        );
    }

    private static void appendElementContent(Element root, String elementName, String elementContent,
            List<Element> currentElements) {
        try {
            Element element = getElement(elementContent);
            int currentElementLastIndex = currentElements.isEmpty() ? 0 : currentElements.size() - 1;
            int indexToAddInRoot = currentElements.isEmpty() ? root.getChildren().size() - 1 :
                    root.indexOf(currentElements.get(currentElementLastIndex));
            root.addContent(indexToAddInRoot + 1, new Text("\n    "));
            root.addContent(indexToAddInRoot + 2, element);
        } catch (Exception e) {
            logError(e, elementName);
        }
    }

    /**
     * Removes {@param appenderName} if it exists in the list of appenders in this {@param root} element.
     */
    public static Optional<Element> removeAppenderIfExists(Element root, Namespace ns, String appenderName) {
        return performForElement(root, ns, ELEMENT_TYPE_APPENDER,
                elementNameEquals(ns, appenderName),
                element -> {
                    log.info("removing old appender: {}", appenderName);
                    root.removeContent(element);
                }
        );
    }

    /**
     * Removes {@param loggerName} if it exists in the list of loggers in this {@param root} element.
     */
    public static Optional<Element> removeLoggerIfExists(Element root, Namespace ns, String loggerName) {
        return performForElement(root, ns, ELEMENT_TYPE_LOGGER,
                elementNameEquals(ns, loggerName),
                element -> {
                    log.info("removing old logger: {}", loggerName);
                    root.removeContent(element);
                }
        );
    }

    /**
     * Return Element object from String
     */
    public static Element getElement(String input) throws IOException, JDOMException {
        SAXBuilder builder = XmlUtils.createSaxBuilder();
        try (InputStream stream = new StringInputStream(input)) {
            Document doc = builder.build(stream);
            Element root = doc.getRootElement();
            return root.detach();
        }
    }

    private static Optional<Element> performForElement(Element root, Namespace ns, String elementType,
            Predicate<Element> filter,
            Consumer<Element> action) {
        Optional<Element> element = root.getChildren(elementType, ns).stream()
                .filter(filter)
                .findAny();
        element.ifPresent(action);
        return element;
    }

    private static void performIfElementNotPresentElement(Element root, Namespace ns, String elementType,
            Predicate<Element> filter, Consumer<Element> elementExistsAction, Runnable action) {
        BiOptional.of(root.getChildren(elementType, ns).stream()
                        .filter(filter)
                        .peek(elementExistsAction)
                        .findAny())
                .ifNotPresent(action);
    }


    private static Predicate<Element> elementNameEquals(Namespace ns, String elementName) {
        return element -> element.getAttributeValue(ATTRIBUTE_NAME, ns).equals(elementName);
    }

    private static Predicate<Element> elementClassEquals(Namespace ns, String elementName) {
        return element -> element.getAttributeValue(ATTRIBUTE_CLASS, ns).equals(elementName);
    }

    public static void logError(Exception e, String elementName) {
        String err = "Error processing '" + elementName + "' element in logback.xml: ";
        log.error("{} {}", err, e.getMessage(), e);
        log.error("Your logback configuration is not in the desired state, please reference the default example " +
                "supplied with the application and manually fix accordingly.");
    }
}
