package org.artifactory.version.converter.v314;

import org.artifactory.version.converter.v160.AddonsLayoutConverterTestBase;
import org.artifactory.version.converter.v312.FixConanDefaultLayoutConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.List;

/**
 * @author volodymyrm
 */
@Test
public class FixConanDefaultLayoutConverterTest extends AddonsLayoutConverterTestBase {

    @Test
    public void testConvertFromPreviousConfigVersion() throws Exception {
        Document document = convertXml("/config/test/config.3.1.4_conan_default_layout.xml",
                new FixConanDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        List<Element> repoLayoutElements = repoLayoutsElement.getChildren();
        checkForFixedDefaultConanV2Layout(repoLayoutElements, namespace);
    }
}