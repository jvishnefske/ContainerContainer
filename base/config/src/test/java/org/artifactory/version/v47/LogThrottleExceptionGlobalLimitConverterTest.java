package org.artifactory.version.v47;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LogThrottleExceptionGlobalLimitConverterTest extends LogbackConverterTest {

    @Test
    void testGlobalLimitPropertyUpdated() throws Exception {
        Document doc = convertXml(
                "/org/artifactory/logging/version/v47/before_throttle_global_limit_update.xml",
                new LogThrottleExceptionGlobalLimitConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();

        assertPropertyExists(root, ns, LogThrottleExceptionGlobalLimitConverter.THROTTLE_EXCEPTION_GLOBAL_LIMIT);

        // Verify the value was updated to default -1
        Element property = getElement(root, ns, "property",
                LogThrottleExceptionGlobalLimitConverter.THROTTLE_EXCEPTION_GLOBAL_LIMIT);
        assertEquals("${log.exceptions.throttle.global.limit:--1}",
                property.getAttributeValue("value"),
                "Property value should be updated to default -1");
        
        // Verify property position is preserved (not moved to end)
        // It should be the 2nd property element (after global-period and before per-logger-period)
        var properties = root.getChildren("property", ns);
        int globalLimitIndex = properties.indexOf(property);
        assertEquals(1, globalLimitIndex, 
                "Property should remain in its original position (index 1), not be moved to the end");
    }
}

