package org.artifactory.convert;

import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.logging.converter.LogbackConverterUtils.ELEMENT_TYPE_APPENDER;
import static org.artifactory.logging.converter.LogbackConverterUtils.ELEMENT_TYPE_LOGGER;
import static org.artifactory.logging.converter.LogbackConverterUtils.ELEMENT_TYPE_PROPERTY;
import static org.artifactory.logging.converter.LogbackConverterUtils.ELEMENT_TYPE_TURBO_FILTER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Dan Feldman
 */
public abstract class LogbackConverterTest extends XmlConverterTest {

    protected void assertLoggerExists(Element root, Namespace ns, String loggerName) {
        assertTrue(elementExists(root, ns, ELEMENT_TYPE_LOGGER, loggerName),
                "Logger '" + loggerName + "' not found after conversion");
    }

    protected void assertLoggerDoesNotExists(Element root, Namespace ns, String loggerName) {
        assertFalse(elementExists(root, ns, ELEMENT_TYPE_LOGGER, loggerName),
                "Logger '" + loggerName + "' found after conversion");
    }


    protected void assertAppenderExists(Element root, Namespace ns, String appenderName) {
        assertTrue(elementExists(root, ns, ELEMENT_TYPE_APPENDER, appenderName),
                "Appender '" + appenderName + "' not found after conversion");
    }

    protected void assertLayoutExists(Element root, Namespace ns, String appenderName, String layoutName) {
        assertEquals(getLayoutInEncoder(root, ns, appenderName), layoutName);
    }

    protected void assertFilterExists(Element root, Namespace ns, String appenderName, String filterClassName) {
        assertNotNull(getFilterByClass(root, ns, appenderName, filterClassName));
    }

    protected void assertTurboFilterExists(Element root, Namespace ns, String turboFilterName) {
        assertTrue(elementExistsByClass(root, ns, ELEMENT_TYPE_TURBO_FILTER, turboFilterName));
    }

    protected void assertPropertyExists(Element root, Namespace ns, String propertyName) {
        assertTrue(elementExists(root, ns, ELEMENT_TYPE_PROPERTY, propertyName));
    }

    protected void assertFilterDoesNotExist(Element root, Namespace ns, String appenderName, String filterClassName) {
        assertNull(getFilterByClass(root, ns, appenderName, filterClassName));
    }

    protected void assertAppenderRefExists(Element root, Namespace ns, String loggerName, String refName) {
        assertTrue(appenderRefExists(root, ns, loggerName, refName));
    }
}
