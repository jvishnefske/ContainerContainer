package org.artifactory.version.v48;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import static org.artifactory.version.v48.BuildHandlerLogConverter.BUILD_HANDLER_LOGGER_NAME;

public class BuildHandlerLogConverterTest extends LogbackConverterTest {

    @Test(testName = "Test build-handler logback entry is added properly")
    public void testLogConversion() throws Exception {
        String resourcePath = "/org/artifactory/logging/version/v48/before_build_handler_log.xml";
        Document doc = convertXml(resourcePath, new BuildHandlerLogConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        assertLoggerExists(root, ns, BUILD_HANDLER_LOGGER_NAME);
    }
}