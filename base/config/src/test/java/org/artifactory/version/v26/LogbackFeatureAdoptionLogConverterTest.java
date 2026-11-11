package org.artifactory.version.v26;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

/**
 * @author Oleksandr Legenkiy
 */
class LogbackFeatureAdoptionLogConverterTest extends LogbackConverterTest {

    @Test
    void testFeatureAdoptionLogAppendersAndLoggers() throws Exception {
        Document doc = convertXml("/org/artifactory/logging/version/v26/before_feature_adoption_log.xml",
                new LogbackFeatureAdoptionLogConverter());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        assertAppenderExists(root, ns, LogbackFeatureAdoptionLogConverter.APPENDER_NAME);
        assertLoggerExists(root, ns, LogbackFeatureAdoptionLogConverter.LOGGER_NAME);
    }
}
