package org.artifactory.version.v32;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

public class MavenIndexerLogConverterTest extends LogbackConverterTest {

    @Test(testName = "Test MavenIndexer logback entry is added properly")
    public void testLogConversion() throws Exception {
        // invoke
        Document doc = convertXml("/org/artifactory/logging/version/v29/before_target_bundle_retention_logback.xml",
                new MavenIndexerLogConverter());

        // verify
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        assertLoggerExists(root, ns, MavenIndexerLogConverter.LOGGER_NAME);
    }
}