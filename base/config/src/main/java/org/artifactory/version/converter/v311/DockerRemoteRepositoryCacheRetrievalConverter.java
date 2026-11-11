package org.artifactory.version.converter.v311;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This converter will update remote repos set to proxy Dockerhub with a its own value, corresponding to the 6 hour
 * limit imposed by Dockerhub. The conditions for the conversions are:
 *
 * 1. It is pointing to Dockerhub
 * 2. The default global value is defined (lower/higher value will not be changed, a warning message will be printed)
 * 3. There are no authentication details set for the remote (e.g. a payed Docker account)
 *
 * @author AndreiK.
 */
public class DockerRemoteRepositoryCacheRetrievalConverter implements XmlConverter {

    private static final Logger log = LoggerFactory.getLogger(DockerRemoteRepositoryCacheRetrievalConverter.class);

    @Override
    public void convert(Document doc) {

        //Skip converter in AOL env.
        if (ConstantValues.aolSkipSkipableConverters.getBoolean()) {
            return;
        }

        log.info("Starting Dockerhub remote repos' default cache retrieval period conversion");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element remoteRepositories = rootElement.getChild("remoteRepositories", namespace);
        if (remoteRepositories != null) {
            for (Element remoteRepo : remoteRepositories.getChildren()) {
                Element pkgTypeElement = remoteRepo.getChild("type", namespace);
                if (pkgTypeElement != null) {
                    String pkgType = pkgTypeElement.getText();
                    if (RepoType.fromType(pkgType).isDockerGroup() && isDockerHubRepo(namespace, remoteRepo)) {
                        updateIfNeeded(namespace, remoteRepo);
                    }
                }
            }
        }
        log.info("Finished Dockerhub remote repos' default cache retrieval period conversion");
    }

    private void updateIfNeeded(Namespace namespace, Element remoteRepo) {
        Element periodSecs = remoteRepo.getChild("retrievalCachePeriodSecs", namespace);
        Element username = remoteRepo.getChild("username", namespace);
        if (periodSecs != null) {
            String periodSecsText = periodSecs.getText();
            boolean isPeriodSecsNotBlank = StringUtils.isNotBlank(periodSecsText);
            boolean isUsernameNotBlank = username != null && StringUtils.isNotBlank(username.getText());
            String repoKey = remoteRepo.getChild("key", namespace).getText();
            log.debug("Checking if Dockerhub repo '{}' is conversion applicable.", repoKey);
            if (isPeriodSecsNotBlank && StringUtils.equals(periodSecsText, "7200") && !isUsernameNotBlank) {
                periodSecs.setText("21600");
                log.debug("Changing previous default value for Docker repo: {}", repoKey);
            } else if (isPeriodSecsNotBlank && Long.parseLong(periodSecsText) < 21600L && !isUsernameNotBlank) {
                log.warn("'{}' Dockerhub repository's retrieval cache period was manually configured with lower value" +
                                " than the recommended '21600' value and missing Dockerhub credentials. It has not" +
                                " been updated. Skipping conversion.",
                        repoKey);
            }
            if (isPeriodSecsNotBlank && isUsernameNotBlank) {
                log.debug("Skipping Dockerhub repo '{}'. Credentials are configured.", repoKey);
            }
        }
    }

    private boolean isDockerHubRepo(Namespace namespace, Element remoteRepo) {
        Element urlElement = remoteRepo.getChild("url", namespace);
        if (urlElement == null) {
            return false;
        }
        String url = urlElement.getText();
        return StringUtils.isNotBlank(url) && StringUtils.containsIgnoreCase(url, "registry-1.docker.io");
    }
}
