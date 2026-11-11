package org.artifactory.version.converter.v300;

import org.artifactory.version.converter.v160.AddonsLayoutConverterTestBase;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.List;

/**
 * @author Uriah Levy
 */
@Test
public class FixGoDefaultLayoutConverterTest extends AddonsLayoutConverterTestBase {

    @Test
    public void testConvertFromPreviousConfigVersion() throws Exception {
        Document document = convertXml("/config/test/config.2.1.4.without_nuget_v3_feed_url.xml",
                new FixGoDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        List<Element> repoLayoutElements = repoLayoutsElement.getChildren();
        checkForFixedDefaultGoLayout(repoLayoutElements, namespace);
    }
}