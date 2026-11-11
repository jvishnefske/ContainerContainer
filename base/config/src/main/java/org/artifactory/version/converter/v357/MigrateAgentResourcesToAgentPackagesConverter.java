package org.artifactory.version.converter.v357;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.util.RepoLayoutUtils.AGENTPACKAGES_DEFAULT_NAME;

/**
 * Migrates repositories with the legacy "agentresources" package type to the renamed "agentpackages" type,
 * and updates their layout ref from "agent-resources-default" to "agent-packages-default".
 */
@Slf4j
public class MigrateAgentResourcesToAgentPackagesConverter implements XmlConverter {

    private static final String LEGACY_TYPE = "agentresources";
    private static final String NEW_TYPE = "agentpackages";
    private static final String LEGACY_LAYOUT = "agent-resources-default";
    private static final String LOCAL_REPOSITORIES = "localRepositories";
    private static final String LOCAL_REPOSITORY = "localRepository";
    private static final String FEDERATED_REPOSITORIES = "federatedRepositories";
    private static final String FEDERATED_REPOSITORY = "federatedRepository";
    private static final String TYPE = "type";
    private static final String KEY = "key";
    private static final String REPO_LAYOUT_REF = "repoLayoutRef";

    @Override
    public void convert(Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        migrateContainer(rootElement, namespace, LOCAL_REPOSITORIES, LOCAL_REPOSITORY);
        migrateContainer(rootElement, namespace, FEDERATED_REPOSITORIES, FEDERATED_REPOSITORY);
    }

    private void migrateContainer(Element rootElement, Namespace namespace, String containerName, String childName) {
        Element container = rootElement.getChild(containerName, namespace);
        if (container == null) {
            return;
        }
        for (Element repo : container.getChildren(childName, namespace)) {
            String type = repo.getChildText(TYPE, namespace);
            if (!LEGACY_TYPE.equals(type)) {
                continue;
            }
            String repoKey = repo.getChildText(KEY, namespace);
            repo.getChild(TYPE, namespace).setText(NEW_TYPE);
            Element layoutRefElement = repo.getChild(REPO_LAYOUT_REF, namespace);
            if (layoutRefElement != null && LEGACY_LAYOUT.equals(layoutRefElement.getText())) {
                layoutRefElement.setText(AGENTPACKAGES_DEFAULT_NAME);
            }
            log.info("Migrated agentresources repo '{}' to agentpackages", repoKey);
        }
    }
}
