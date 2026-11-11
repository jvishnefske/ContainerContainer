package org.artifactory.version.v35;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

public class ShadowLogConverterTest extends LogbackConverterTest {

    @Test(testName = "Test shadow logback entry is added properly")
    public void testLogConversion() throws Exception {
        // invoke
        Document doc = convertXml("/org/artifactory/logging/version/v35/before_shadow_log.xml",
                new ShadowLogsConverter());

        // verify
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        assertAppenderExists(root, ns, ShadowLogsConverter.SHADOW_APPENDER_NAME);
        assertLoggerExists(root, ns, ShadowLogsConverter.SHADOW_LOGGER_NAME);
    }
}