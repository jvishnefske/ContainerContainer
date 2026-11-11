package org.artifactory.version.converter.v352;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.artifactory.version.converter.v160.AddonsDefaultLayoutConverterHelper;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import java.util.List;

import static org.artifactory.util.RepoLayoutUtils.SKILLS_DEFAULT_NAME;

@Slf4j
public class AddSkillsDefaultLayoutConverter implements XmlConverter {

    private static final String NAME = "name";
    private static final String REPO_LAYOUTS = "repoLayouts";
    private static final String SKILLS_TYPE = "skills";
    private static final String REPO_LAYOUT_REF = "repoLayoutRef";

    @Override
    public void convert(Document doc) {
        log.info("Starting 'Add Skills default repository layout' conversion");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild(REPO_LAYOUTS, namespace);
        if (!alreadyExists(repoLayoutsElement)) {
            addLayout(repoLayoutsElement, namespace);
        }
        updateSkillsRepoLayouts(rootElement, namespace);
        log.info("Finished 'Add Skills default repository layout' conversion");
    }

    private void updateSkillsRepoLayouts(Element rootElement, Namespace namespace) {
        updateReposInContainer(rootElement, namespace, "localRepositories", "localRepository");
        updateReposInContainer(rootElement, namespace, "federatedRepositories", "federatedRepository");
    }

    private void updateReposInContainer(Element rootElement, Namespace namespace, String containerName,
            String childName) {
        Element container = rootElement.getChild(containerName, namespace);
        if (container == null) {
            return;
        }
        List<Element> repos = container.getChildren(childName, namespace);
        for (Element repo : repos) {
            String type = repo.getChildText("type", namespace);
            String layoutRef = repo.getChildText(REPO_LAYOUT_REF, namespace);
            if (SKILLS_TYPE.equals(type) && !SKILLS_DEFAULT_NAME.equals(layoutRef)) {
                String repoKey = repo.getChildText("key", namespace);
                log.info("Updating layout for skills repo '{}'" + " from '{}' to '{}'", repoKey, layoutRef,
                        SKILLS_DEFAULT_NAME);
                repo.getChild(REPO_LAYOUT_REF, namespace).setText(SKILLS_DEFAULT_NAME);
            }
        }
    }

    private boolean alreadyExists(Element repoLayoutsElement) {
        return repoLayoutsElement.getChildren().stream().anyMatch(
                element -> element.getChildren().stream().filter(e -> NAME.equals(e.getName()))
                        .anyMatch(e -> SKILLS_DEFAULT_NAME.equals(e.getContent().getFirst().getValue())));
    }

    private void addLayout(Element repoLayoutsElement, Namespace namespace) {
        log.info("Adding Skills default repository layout");
        repoLayoutsElement.addContent(
                AddonsDefaultLayoutConverterHelper.getRepoLayoutElement(repoLayoutsElement, namespace,
                        SKILLS_DEFAULT_NAME, "[module]/[version]/[module]-[version].[ext]", "false", null, ".*", ".*"));
    }
}
