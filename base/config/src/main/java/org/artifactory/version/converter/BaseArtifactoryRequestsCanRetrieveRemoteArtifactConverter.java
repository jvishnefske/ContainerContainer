package org.artifactory.version.converter;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.repo.PackageType;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import java.util.List;

/**
 * As part of the package-handler implementation, we are enforcing the value of
 * artifactoryRequestsCanRetrieveRemoteArtifacts.
 * In order to not breaks lots of customers, we created this converter and changed the default value of
 * artifactoryRequestsCanRetrieveRemoteArtifacts to true,
 * Because in most packages in the package-indexer we didn't take this flag into consideration (basically all the
 * packages that implement a download of a package in their API, are not considering this flag)
 * For more info see RTDEV-26892
 */
@Slf4j
public abstract class BaseArtifactoryRequestsCanRetrieveRemoteArtifactConverter implements XmlConverter {
    private static final String SEARCHED_ELEMENT = "artifactoryRequestsCanRetrieveRemoteArtifacts";
    private final String name;

    /**
     * @param packageType - package type of the target package type
     */
    public BaseArtifactoryRequestsCanRetrieveRemoteArtifactConverter(
            PackageType packageType) {
        this.name = packageType.name().toLowerCase();
    }

    @Override
    public void convert(Document doc) {
        log.info("Starting 'fix {} {}' conversion", name, SEARCHED_ELEMENT);
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element virtualRepositoriesElement = rootElement.getChild("virtualRepositories", namespace);
        List<Element> virtualRepositories = virtualRepositoriesElement.getChildren();

        for (Element virtualRepo : virtualRepositories) {
            if (virtualRepo.getContent() != null && virtualRepo.getChild("type", namespace) != null
                    && name.equals(virtualRepo.getChild("type", namespace).getText()) &&
                    !Boolean.parseBoolean(virtualRepo.getChild(SEARCHED_ELEMENT, namespace).getText())) {
                String repoName = virtualRepo.getChild("key", namespace).getText();
                log.debug("Found {} virtual repository {}", name, repoName);
                Element artifactoryRequestsCanRetrieveRemoteArtifactsElement =
                        virtualRepo.getChild(SEARCHED_ELEMENT, namespace);
                artifactoryRequestsCanRetrieveRemoteArtifactsElement.setText("true");
                log.debug("Fixed {} virtual repository {}", name, repoName);
            }
        }
        log.info("Finished 'fix {} {}' conversion successfully", name, SEARCHED_ELEMENT);
    }
}