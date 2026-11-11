package org.artifactory.version.converter.v313;

import org.apache.commons.collections4.CollectionUtils;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

import static org.artifactory.descriptor.repo.LocalRepoDescriptor.DEFAULT_DOCKER_TAG_RETENTION;

/**
 * @author Tomer Nir
 */
public class DockerLocalRepositoryRetentionPolicyConverter implements XmlConverter {

    private static final Logger log = LoggerFactory.getLogger(DockerLocalRepositoryRetentionPolicyConverter.class);
    static final String DOCKER_RETENTION_FIELD_NAME = "dockerTagRetention";

    @Override
    public void convert(Document doc) {
        log.info("Started {}", this.getClass().getSimpleName());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        convertRepositories(rootElement, namespace);
        log.info("Finished {}", this.getClass().getSimpleName());
    }

    private void convertRepositories(Element rootElement, Namespace namespace) {
        log.debug("Converting local repositories");
        Element repos = rootElement.getChild("localRepositories", namespace);
        if (repos != null) {
            List<Element> repositories = repos.getChildren("localRepository", namespace);
            if (CollectionUtils.isNotEmpty(repositories)) {
                for (Element repository : repositories) {
                    if (isDockerRepo(repository, namespace)) {
                        Element retentionPolicy = repository.getChild(DOCKER_RETENTION_FIELD_NAME, namespace);
                        if (retentionPolicy == null) {
                            appendDockerTagRetention(repository, namespace);
                        }
                    }
                }
            }
        }
    }

    static boolean isDockerRepo(Element repository, Namespace namespace) {
        return "docker".equals(repository.getChild("type", namespace).getText());
    }

    private void appendDockerTagRetention(Element repository, Namespace namespace) {
        Element dockerTagRetentionElement = new Element(DOCKER_RETENTION_FIELD_NAME, namespace);
        dockerTagRetentionElement.setText(String.valueOf(DEFAULT_DOCKER_TAG_RETENTION));

        appendElementAfterAll(repository, dockerTagRetentionElement, namespace,
                "type", "description", "notes", "includesPattern", "excludesPattern",
                "repoLayoutRef", "dockerApiVersion", "keyPairRef", "forceNugetAuthentication", "blackedOut",
                "handleReleases",
                "handleSnapshots", "maxUniqueSnapshots", "maxUniqueTags", "blockPushingSchema1", "propertySets",
                "propertySetRef", "archiveBrowsingEnabled", "RepoXrayConfigType", "downloadRedirect", "cdnRedirect",
                "suppressPomConsistencyChecks",
                "snapshotVersionBehavior", "localRepoChecksumPolicyType", "calculateYumMetadata",
                "yumRootDepth", "yumGroupFileNames", "debianTrivialLayout",
                "enableFileListsIndexing", "optionalIndexCompressionFormats");
    }

    private void appendElementAfterAll(Element appendTo, Element toAppend, Namespace namespace,
            String... elementNamesToAppendAfter) {
        int indexToAppendAfter = getIndexOfLastFoundElement(appendTo, namespace, elementNamesToAppendAfter);
        appendTo.addContent(indexToAppendAfter + 1, toAppend);
    }

    private int getIndexOfLastFoundElement(Element repositoryElement, Namespace namespace, String... elementNames) {
        int index = -1;
        for (String elementName : elementNames) {
            Element child = repositoryElement.getChild(elementName, namespace);
            if (child != null) {
                index = Math.max(index, repositoryElement.indexOf(child));
            }
        }
        return index;
    }
}