package org.artifactory.version.converter.v313;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import java.util.List;
import java.util.Optional;

/**
 * @author Volodymyr Shulga
 */
@Slf4j
public class DistributionReposToLocalConverter implements XmlConverter {

    @Override
    public void convert(@NonNull Document doc) {
        String simpleName = this.getClass().getSimpleName();
        log.info("Started {}", simpleName);
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element distributionRepositories = rootElement.getChild("distributionRepositories", namespace);
        if (distributionRepositories != null) {
            List<Element> repositories = distributionRepositories.getChildren("distributionRepository", namespace);
            if (CollectionUtils.isNotEmpty(repositories)) {
                final String type = "generic";
                final String dockerApiVersion = "V2";
                final String forceNugetAuthentication = "false";
                final String handleReleases = "true";
                final String maxUniqueSnapshots = "0";
                final String suppressPomConsistencyChecks = "true";
                final String archiveBrowsingEnabled = "false";

                Element localRepositories = rootElement.getChild("localRepositories", namespace);
                for (Element repository : repositories) {
                    Element localRepo = new Element("localRepository", namespace);
                    calculateAndAddAttribute(repository, localRepo, namespace, "key");
                    addAttribute(localRepo, namespace, "type", type);
                    calculateAndAddAttribute(repository, localRepo, namespace, "includesPattern");
                    calculateAndAddAttribute(repository, localRepo, namespace, "repoLayoutRef");
                    addAttribute(localRepo, namespace, "dockerApiVersion", dockerApiVersion);
                    addAttribute(localRepo, namespace, "forceNugetAuthentication", forceNugetAuthentication);
                    calculateAndAddAttribute(repository, localRepo, namespace, "blackedOut");
                    addAttribute(localRepo, namespace, "handleReleases", handleReleases);
                    addAttribute(localRepo, namespace, "maxUniqueSnapshots", maxUniqueSnapshots);
                    addAttribute(localRepo, namespace, "suppressPomConsistencyChecks", suppressPomConsistencyChecks);
                    addAttribute(localRepo, namespace, "archiveBrowsingEnabled", archiveBrowsingEnabled);
                    calculateAndAddAttribute(repository, localRepo, namespace, "snapshotVersionBehavior");
                    calculateAndAddAttribute(repository, localRepo, namespace, "localRepoChecksumPolicyType");
                    calculateAndAddAttribute(repository, localRepo, namespace, "calculateYumMetadata");
                    calculateAndAddAttribute(repository, localRepo, namespace, "yumRootDepth");
                    calculateAndAddAttribute(repository, localRepo, namespace, "debianTrivialLayout");
                    log.info("Migrated Bintray distribution repository {} type to local generic", repository.getName());
                    localRepositories.addContent(localRepo);
                }
            }
        }
        // remove bintrayApplicationRef from federatedRepositories
        Element federatedRepositories = rootElement.getChild("federatedRepositories", namespace);
        if (federatedRepositories != null) {
            List<Element> repositories = federatedRepositories.getChildren("federatedRepository", namespace);
            for (Element repository : repositories) {
                repository.removeChild("bintrayApplicationRef", namespace);
            }
        }

        rootElement.removeChild("distributionRepositories", namespace);
        rootElement.removeChild("bintrayConfig", namespace);
        rootElement.removeChild("bintrayApplications", namespace);
        log.info("Finished {}", simpleName);
    }

    private void addAttribute(@NonNull Element repo, Namespace namespace, @NonNull String name, @NonNull String value) {
        repo.addContent(new Element(name, namespace).setText(value));
    }

    private void calculateAndAddAttribute(@NonNull Element distributionRepo, @NonNull Element localRepo,
            Namespace namespace, @NonNull String name) {
        String value = getChildValue(distributionRepo, name, namespace);
        if (value != null) {
            localRepo.addContent(new Element(name, namespace).setText(value));
        }
    }

    private String getChildValue(Element repository, String name, Namespace namespace) {
        return Optional.ofNullable(repository.getChild(name, namespace)).map(Element::getText).orElse(null);
    }
}
