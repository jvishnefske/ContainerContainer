package org.artifactory.version.v49;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import static org.artifactory.version.v49.LogbackErrorCatalogAppenderConverter.ERROR_CATALOG_APPENDER;
import static org.testng.Assert.assertTrue;

public class LogbackErrorCatalogAppenderConverterTest extends LogbackConverterTest {

    private static final String RESOURCE = "/org/artifactory/logging/version/v49/before_error_catalog_log.xml";

    @Test(testName = "ERROR_CATALOG appender and root appender-ref are added")
    public void testLogConversion() throws Exception {
        Document doc = convertXml(RESOURCE, new LogbackErrorCatalogAppenderConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();

        assertAppenderExists(root, ns, ERROR_CATALOG_APPENDER);
        assertTrue(rootHasAppenderRef(root, ERROR_CATALOG_APPENDER),
                "ERROR_CATALOG appender-ref must be added to <root>");
    }

    @Test(testName = "Conversion is idempotent - no duplicate appender or root ref")
    public void testConversionIsIdempotent() throws Exception {
        LogbackErrorCatalogAppenderConverter converter = new LogbackErrorCatalogAppenderConverter();
        Document doc = convertXml(RESOURCE, converter);
        converter.convert(doc);
        Element root = doc.getRootElement();

        long appenderCount = root.getChildren("appender", root.getNamespace()).stream()
                .filter(a -> ERROR_CATALOG_APPENDER.equals(a.getAttributeValue("name")))
                .count();
        long rootRefCount = root.getChild("root").getChildren("appender-ref").stream()
                .filter(ref -> ERROR_CATALOG_APPENDER.equals(ref.getAttributeValue("ref")))
                .count();
        org.testng.Assert.assertEquals(appenderCount, 1L, "appender must not be duplicated");
        org.testng.Assert.assertEquals(rootRefCount, 1L, "root appender-ref must not be duplicated");
    }

    private boolean rootHasAppenderRef(Element root, String refName) {
        Element rootLogger = root.getChild("root");
        return rootLogger != null && rootLogger.getChildren("appender-ref").stream()
                .anyMatch(ref -> refName.equals(ref.getAttributeValue("ref")));
    }
}
