package org.artifactory.version.converter.v3126;

import org.artifactory.version.converter.v160.AddonsLayoutConverterTestBase;
import org.artifactory.version.converter.v3127.FixSwiftDefaultLayoutConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.List;

@Test
public class FixSwiftDefaultLayoutConverterTest extends AddonsLayoutConverterTestBase {

    @Test
    public void testConvertFromPreviousConfigVersion() throws Exception {
        Document document = convertXml("/config/test/config.3.1.26_convert_nuget_symbol_server_url.xml",
                new FixSwiftDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        List<Element> repoLayoutElements = repoLayoutsElement.getChildren();
        checkForFixedDefaultSwiftLayout(repoLayoutElements, namespace);
    }

}