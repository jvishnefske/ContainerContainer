package org.artifactory.version.converter.v3123;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.artifactory.version.converter.v160.AddonsDefaultLayoutConverterHelper;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

/**
 * For version 3.1.23
 *
 * @author BarakH
 */
@Slf4j
public class AddTerraformDefaultLayoutConverter implements XmlConverter {

    public static final String TERRAFORM_MODULE_LAYOUT = "terraform-module-default";
    public static final String TERRAFORM_PROVIDER_LAYOUT = "terraform-provider-default";

    @Override
    public void convert(Document doc) {
        log.info("Starting 'Add Terraform default repositories layouts' conversion");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        if (!alreadyExists(repoLayoutsElement)) {
            addTerraformDefaultLayouts(repoLayoutsElement, namespace);
        }
        log.info("Finished 'Add Terraform default repositories layouts' conversion successfully");
    }

    private boolean alreadyExists(Element repoLayoutsElement) {
        for (Element element : repoLayoutsElement.getChildren()) {
            boolean match = element.getChildren().stream()
                    .filter(e -> e.getName().equals("name"))
                    .anyMatch(e -> TERRAFORM_MODULE_LAYOUT.equals(e.getContent().get(0).getValue()) ||
                            TERRAFORM_PROVIDER_LAYOUT.equals(e.getContent().get(0).getValue()));
            if (match) {
                return true;
            }
        }
        return false;
    }

    private void addTerraformDefaultLayouts(Element repoLayoutsElement, Namespace namespace) {
        repoLayoutsElement
                .addContent(AddonsDefaultLayoutConverterHelper.getRepoLayoutElement(repoLayoutsElement, namespace,
                        TERRAFORM_MODULE_LAYOUT,
                        "[namespace]/[module-name]/[provider]/[version].[ext]",
                        "false", null,
                        ".*",
                        ".*"));
        repoLayoutsElement
                .addContent(AddonsDefaultLayoutConverterHelper.getRepoLayoutElement(repoLayoutsElement, namespace,
                        TERRAFORM_PROVIDER_LAYOUT,
                        "[namespace]/[provider-name]/[version]/terraform-provider-[provider-name]_[version]_[os]_" +
                                "[arch].[ext]",
                        "false", null,
                        ".*",
                        ".*"));
    }
}