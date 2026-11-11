package org.artifactory.version.converter.v3123;

import org.artifactory.version.converter.v160.AddonsLayoutConverterTestBase;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.List;

import static org.artifactory.version.converter.v3123.AddTerraformDefaultLayoutConverter.TERRAFORM_MODULE_LAYOUT;
import static org.artifactory.version.converter.v3123.AddTerraformDefaultLayoutConverter.TERRAFORM_PROVIDER_LAYOUT;

/**
 * @author BarakH
 */
public class AddTerraformDefaultLayoutConverterTest extends AddonsLayoutConverterTestBase {

    @Test
    public void testConvertFromPreviousConfigVersion() throws Exception {
        Document document = convertXml("/config/test/config.3.1.5_with-server-name.xml",
                new AddTerraformDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        List<Element> repoLayoutElements = repoLayoutsElement.getChildren();

        checkLayout(repoLayoutElements, namespace, TERRAFORM_MODULE_LAYOUT,
                "[namespace]/[module-name]/[provider]/[version].[ext]",
                "false", null,
                ".*",
                ".*");


        checkLayout(repoLayoutElements, namespace, TERRAFORM_PROVIDER_LAYOUT,
                "[namespace]/[provider-name]/[version]/terraform-provider-[provider-name]_[version]_[os]_" +
                        "[arch].[ext]",
                "false", null,
                ".*",
                ".*");
    }
}