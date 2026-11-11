package org.artifactory.version.converter.v3110;

import org.artifactory.version.converter.v160.AddonsLayoutConverterTestBase;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.List;

/**
 * @author Rotem Kfir
 */
@Test
public class AddCargoDefaultLayoutConverterTest extends AddonsLayoutConverterTestBase {

    @Test
    public void testConvertFromPreviousConfigVersion() throws Exception {
        Document document = convertXml("/config/test/config.3.1.5_with-server-name.xml",
                new AddCargoDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        List<Element> repoLayoutElements = repoLayoutsElement.getChildren();

        checkLayout(repoLayoutElements, namespace, "cargo-default",
                "crates/[module]/[module]-[baseRev].[ext]",
                "false",
                null,
                ".*",
                ".*");
    }
}