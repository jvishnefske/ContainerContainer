package org.artifactory.version.v34;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

public class BundleHandlerLogConverterTest extends LogbackConverterTest {

    @Test(testName = "Test bundle handler logback entry is added properly")
    public void testLogConversion() throws Exception {
        // invoke
        Document doc = convertXml("/org/artifactory/logging/version/v34/before_bundlehandler_log.xml",
                new BundleHandlerLogConverter());

        // verify
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        assertLoggerExists(root, ns, BundleHandlerLogConverter.BUNDLE_HANDLER_CRYPTO_LOGGER_NAME);
        assertLoggerExists(root, ns, BundleHandlerLogConverter.BUNDLE_HANDLER_LOGGER_NAME);
    }
}