package org.artifactory.version.converter.v360;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import java.util.ArrayList;
import java.util.List;

import static org.artifactory.util.RepoLayoutUtils.AGENTPACKAGES_DEFAULT_NAME;

/**
 * Removes the legacy {@code agent-resources-default} repository layout definition and rewrites any remaining
 * {@code repoLayoutRef} values to {@code agent-packages-default}.
 */
@Slf4j
public class RemoveAgentResourcesDefaultLayoutConverter implements XmlConverter {

    private static final String LEGACY_LAYOUT = "agent-resources-default";
    private static final String REPO_LAYOUTS = "repoLayouts";
    private static final String REPO_LAYOUT = "repoLayout";
    private static final String NAME = "name";
    private static final String KEY = "key";
    private static final String REPO_LAYOUT_REF = "repoLayoutRef";

    private static final String[][] REPO_CONTAINERS = {
            {"localRepositories", "localRepository"},
            {"federatedRepositories", "federatedRepository"},
            {"remoteRepositories", "remoteRepository"},
            {"virtualRepositories", "virtualRepository"}
    };

    @Override
    public void convert(Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        rewriteLegacyLayoutRefs(rootElement, namespace);
        removeLegacyLayoutDefinition(rootElement, namespace);
    }

    private void rewriteLegacyLayoutRefs(Element rootElement, Namespace namespace) {
        for (String[] container : REPO_CONTAINERS) {
            rewriteContainerRefs(rootElement, namespace, container[0], container[1]);
        }
    }

    private void rewriteContainerRefs(Element rootElement, Namespace namespace, String containerName,
            String childName) {
        Element container = rootElement.getChild(containerName, namespace);
        if (container == null) {
            return;
        }
        for (Element repo : container.getChildren(childName, namespace)) {
            Element layoutRefElement = repo.getChild(REPO_LAYOUT_REF, namespace);
            if (layoutRefElement != null && LEGACY_LAYOUT.equals(layoutRefElement.getText())) {
                String repoKey = repo.getChildText(KEY, namespace);
                layoutRefElement.setText(AGENTPACKAGES_DEFAULT_NAME);
                log.info("Rewrote repoLayoutRef for repo '{}' from '{}' to '{}'", repoKey, LEGACY_LAYOUT,
                        AGENTPACKAGES_DEFAULT_NAME);
            }
        }
    }

    private void removeLegacyLayoutDefinition(Element rootElement, Namespace namespace) {
        Element repoLayoutsElement = rootElement.getChild(REPO_LAYOUTS, namespace);
        if (repoLayoutsElement == null) {
            return;
        }
        List<Element> layoutsToRemove = new ArrayList<>();
        for (Element repoLayout : repoLayoutsElement.getChildren(REPO_LAYOUT, namespace)) {
            if (LEGACY_LAYOUT.equals(repoLayout.getChildText(NAME, namespace))) {
                layoutsToRemove.add(repoLayout);
            }
        }
        for (Element repoLayout : layoutsToRemove) {
            repoLayoutsElement.removeContent(repoLayout);
            log.info("Removed legacy repo layout '{}'", LEGACY_LAYOUT);
        }
    }
}
