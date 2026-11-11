package org.artifactory.version.converter.v3111;

import jakarta.annotation.Nonnull;
import org.apache.commons.collections4.CollectionUtils;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jfrog.common.StreamSupportUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Omri Ziv
 */
public class RemoveKeyPairFromRemoteRepositoriesConverter implements XmlConverter {

    private static final Logger log = LoggerFactory.getLogger(RemoveKeyPairFromRemoteRepositoriesConverter.class);
    private static final String PRIMARY_KEY_PAIR_REF_KEY = "primaryKeyPairRef";
    private static final String SECONDARY_KEY_PAIR_REF_KEY = "secondaryKeyPairRef";

    @Override
    public void convert(Document doc) {
        log.info("Started {}", this.getClass().getSimpleName());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        List<Element> repos = getRemoteRepositoriesElements(rootElement, namespace);
        removeKeyPairsFromRemoteRepositories(repos, namespace);
        log.info("Finished {}", this.getClass().getSimpleName());
    }


    private void removeKeyPairsFromRemoteRepositories(List<Element> repos, Namespace namespace) {
        StreamSupportUtils.stream(repos)
                .peek(repo -> log.info("Removing key-pair reference from remote repo: {}", repo))
                .forEach(repository -> {
                    Element primaryKeyPairRef = repository.getChild(PRIMARY_KEY_PAIR_REF_KEY, namespace);
                    Element secondaryKeyPairRef = repository.getChild(SECONDARY_KEY_PAIR_REF_KEY, namespace);
                    if (primaryKeyPairRef != null) {
                        repository.removeChild(PRIMARY_KEY_PAIR_REF_KEY, namespace);
                    }
                    if (secondaryKeyPairRef != null) {
                        repository.removeChild(SECONDARY_KEY_PAIR_REF_KEY, namespace);
                    }
                });
    }

    @Nonnull
    private List<Element> getRemoteRepositoriesElements(Element rootElement, Namespace namespace) {
        Element remoteRepositories = rootElement.getChild("remoteRepositories", namespace);
        List<Element> repos = new ArrayList<>();
        if (remoteRepositories != null) {
            List<Element> children = remoteRepositories.getChildren("remoteRepository", namespace);
            if (CollectionUtils.isNotEmpty(children)) {
                repos.addAll(children);
            }
        }
        return repos;
    }

}
