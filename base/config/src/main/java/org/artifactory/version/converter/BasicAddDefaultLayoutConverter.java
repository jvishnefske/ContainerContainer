package org.artifactory.version.converter;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.v160.AddonsDefaultLayoutConverterHelper;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

/**
 * Base class for all layouts convertors
 *
 * @author Asaf Zalcman
 */
@Slf4j
public class BasicAddDefaultLayoutConverter implements XmlConverter {

    private final String name;
    private final String artifactPathPattern;
    private final String distinctiveDescriptorPathPattern;
    private final String descriptorPathPattern;
    private final String folderIntegrationRevisionRegExp;
    private final String fileIntegrationRevisionRegExp;

    public BasicAddDefaultLayoutConverter(String name,
            String artifactPathPattern,
            String distinctiveDescriptorPathPattern, String descriptorPathPattern,
            String folderIntegrationRevisionRegExp, String fileIntegrationRevisionRegExp) {
        this.name = name;
        this.artifactPathPattern = artifactPathPattern;
        this.distinctiveDescriptorPathPattern = distinctiveDescriptorPathPattern;
        this.descriptorPathPattern = descriptorPathPattern;
        this.folderIntegrationRevisionRegExp = folderIntegrationRevisionRegExp;
        this.fileIntegrationRevisionRegExp = fileIntegrationRevisionRegExp;
    }

    @Override
    public void convert(Document doc) {
        log.info("Starting 'Add {} repositories layouts' conversion", name);
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        if (!alreadyExists(repoLayoutsElement)) {
            addDefaultLayout(repoLayoutsElement, namespace);
        }
        log.info("Finished 'Add {} repositories layouts' conversion successfully", name);
    }

    private boolean alreadyExists(Element repoLayoutsElement) {
        for (Element element : repoLayoutsElement.getChildren()) {
            boolean match = element.getChildren().stream()
                    .filter(e -> e.getName().equals("name"))
                    .anyMatch(e -> name.equals(e.getContent().get(0).getValue()));
            if (match) {
                return true;
            }
        }
        return false;
    }

    private void addDefaultLayout(Element repoLayoutsElement, Namespace namespace) {
        repoLayoutsElement.addContent(
                AddonsDefaultLayoutConverterHelper.getRepoLayoutElement(repoLayoutsElement, namespace, name,
                        artifactPathPattern, distinctiveDescriptorPathPattern, descriptorPathPattern,
                        folderIntegrationRevisionRegExp, fileIntegrationRevisionRegExp));
    }
}