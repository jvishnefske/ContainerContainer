package org.artifactory.version.v23;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

public class LogbackRemoveStartArtifactoryProWithServicesLoggerTest extends LogbackConverterTest {

    @Test
    public void test() throws Exception {
        Document doc = convertXml(
                "/org/artifactory/logging/version/v23/before_remove_StartArtifactoryProWithServices_logger.xml",
                new LogbackRemoveStartArtifactoryProWithServicesLogger());

        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();

        assertLoggerDoesNotExists(root, ns, "org.artifactory.webapp.main.StartArtifactoryProWithServices");
    }
}
