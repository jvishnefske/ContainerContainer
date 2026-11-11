package org.artifactory.version.v40;

import org.artifactory.convert.LogbackConverterTest;
import org.artifactory.version.v29.TargetBundleRetentionLogConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author Hezi Cohen
 * 25/02/2024
 */
public class DistributionHandlerLogConverterTest extends LogbackConverterTest {

    @Test(testName = "Test distribution-handler logback entry is added properly", dataProvider = "getXmls")
    public void testLogConversion(String xmlName) throws Exception {
        // invoke
        Document doc = convertXml(
                "/org/artifactory/logging/version/v40/" + xmlName,
                new DistributionHandlerLogConverter());

        // verify
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        assertLoggerExists(root, ns, DistributionHandlerLogConverter.LOGGER_NAME);
        assertLoggerDoesNotExists(root, ns, TargetBundleRetentionLogConverter.LOGGER_NAME);
    }

    @DataProvider
    private Object[][] getXmls() {
        return new Object[][] {
                {"logback_before_log_distribution_handler.xml"},
                {"logback_before_log_distribution_handler_no_retention.xml"}
        };
    }
}