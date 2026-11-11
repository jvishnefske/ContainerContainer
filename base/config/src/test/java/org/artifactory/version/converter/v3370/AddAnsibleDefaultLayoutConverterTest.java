package org.artifactory.version.converter.v3370;

import org.artifactory.util.RepoLayoutUtils;
import org.artifactory.version.converter.v160.AddonsLayoutConverterTestBase;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.List;

/**
 * For version 3.3.7
 *
 * @author BarakH
 */
public class AddAnsibleDefaultLayoutConverterTest extends AddonsLayoutConverterTestBase {

    @Test
    public void testConvertFromPreviousConfigVersion() throws Exception {
        Document document = convertXml("/config/test/config.3.3.6_without_ansible_layout.xml",
                                       new AddAnsibleDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        List<Element> repoLayoutElements = repoLayoutsElement.getChildren();

        checkLayout(repoLayoutElements, namespace, RepoLayoutUtils.ANSIBLE_DEFAULT_NAME,
                    "collections/[namespace]/[name]/[version]/[namespace]-[name]-[version].tar.gz",
                    "false", null,
                    ".*",
                    ".*");
    }

}