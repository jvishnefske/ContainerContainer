package org.artifactory.version.converter;

import lombok.extern.slf4j.Slf4j;
import org.jdom2.Element;
import org.jdom2.Namespace;

/**
 * Migrates all local and federated repositories of a given package type to use a new
 * default repository layout. Skips repositories that are already on the target layout.
 *
 * @author Linoy Levi
 */
@Slf4j
public final class RepoLayoutTypeMigrationHelper {

    private static final String LOCAL_REPOSITORIES = "localRepositories";
    private static final String LOCAL_REPOSITORY = "localRepository";
    private static final String FEDERATED_REPOSITORIES = "federatedRepositories";
    private static final String FEDERATED_REPOSITORY = "federatedRepository";
    private static final String TYPE = "type";
    private static final String KEY = "key";
    private static final String REPO_LAYOUT_REF = "repoLayoutRef";

    private RepoLayoutTypeMigrationHelper() {
    }

    public static void migrateExistingRepos(Element rootElement, Namespace namespace, String repoType,
            String newLayoutName) {
        migrateContainer(rootElement, namespace, LOCAL_REPOSITORIES, LOCAL_REPOSITORY, repoType, newLayoutName);
        migrateContainer(rootElement, namespace, FEDERATED_REPOSITORIES, FEDERATED_REPOSITORY, repoType,
                newLayoutName);
    }

    private static void migrateContainer(Element rootElement, Namespace namespace, String containerName,
            String childName, String repoType, String newLayoutName) {
        Element container = rootElement.getChild(containerName, namespace);
        if (container == null) {
            return;
        }
        for (Element repo : container.getChildren(childName, namespace)) {
            String type = repo.getChildText(TYPE, namespace);
            String layoutRef = repo.getChildText(REPO_LAYOUT_REF, namespace);
            if (repoType.equals(type) && !newLayoutName.equals(layoutRef)) {
                String repoKey = repo.getChildText(KEY, namespace);
                log.info("Migrating {} repo '{}' layout from '{}' to '{}'", repoType, repoKey, layoutRef,
                        newLayoutName);
                repo.getChild(REPO_LAYOUT_REF, namespace).setText(newLayoutName);
            }
        }
    }
}
