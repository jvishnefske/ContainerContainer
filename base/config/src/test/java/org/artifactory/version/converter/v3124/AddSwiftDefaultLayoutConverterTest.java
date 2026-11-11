package org.artifactory.version.converter.v3124;

import org.artifactory.version.converter.v160.AddonsLayoutConverterTestBase;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import java.util.List;


/**
 * @author Asaf Zalcman
 */
public class AddSwiftDefaultLayoutConverterTest extends AddonsLayoutConverterTestBase {

    @Test
    public void testConvertFromPreviousConfigVersion() throws Exception {
        Document document = convertXml("/config/test/config.3.1.5_with-server-name.xml",
                new AddSwiftDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        List<Element> repoLayoutElements = repoLayoutsElement.getChildren();

        checkLayout(repoLayoutElements, namespace, "swift-default",
                "[scope]/[name]/[version].zip",
                "false", null,
                ".*",
                ".*");
    }

}