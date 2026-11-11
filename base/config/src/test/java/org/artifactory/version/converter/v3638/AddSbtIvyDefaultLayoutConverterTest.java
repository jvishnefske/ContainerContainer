package org.artifactory.version.converter.v3638;

import org.artifactory.util.RepoLayoutUtils;
import org.artifactory.version.converter.v160.AddonsLayoutConverterTestBase;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.List;

/**
 * @author Natalie Berni
 */
public class AddSbtIvyDefaultLayoutConverterTest extends AddonsLayoutConverterTestBase {

    @Test
    public void testConvertFromPreviousConfigVersion() throws Exception {
        Document document = convertXml("/config/test/config.3.4.4_without_sbt_ivy_layout.xml",
                new AddSbtIvyDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        List<Element> repoLayoutElements = repoLayoutsElement.getChildren();

        checkLayout(repoLayoutElements, namespace, RepoLayoutUtils.SBT_IVY_DEFAULT_NAME,
                "[orgPath]/[module]/[baseRev](-[folderItegRev])/" +
                        "[module]-[baseRev](-[fileItegRev])(-[classifier]).[ext]",
                "true",
                "[orgPath]/[module]/[baseRev](-[folderItegRev])/" +
                        "ivy-[baseRev](-[fileItegRev])(-[classifier]).xml",
                "\\d{14}",
                "\\d{14}");
    }
}
