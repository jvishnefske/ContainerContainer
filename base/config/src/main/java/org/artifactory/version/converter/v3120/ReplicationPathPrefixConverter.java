package org.artifactory.version.converter.v3120;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import java.util.ArrayList;
import java.util.List;

/**
 * This converter will convert exist replication path prefix field to include/exclude path prefix patterns.
 *
 * @author Michael Akushsky
 */
@Slf4j
public class ReplicationPathPrefixConverter implements XmlConverter {

    @Override
    public void convert(Document doc) {
        log.info("Starting conversion of exist replication path prefix field to include/exclude path prefix patterns.");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        List<Element> replications = getLocalAndRemoteReplicationsElements(rootElement, namespace);

        for (Element replication : replications) {
            Element pathPrefixElement = replication.getChild("pathPrefix", namespace);

            String pathPrefix = null;
            if (pathPrefixElement != null) {
                pathPrefix = pathPrefixElement.getText();
                replication.removeChild("pathPrefix", namespace);
            }

            log.info("Enabling include/exclude replication path prefix for {} repository",
                    replication.getChild("repoKey", namespace).getText());

            Element includePathPrefixPattern = new Element("includePathPrefixPattern", namespace);
            includePathPrefixPattern.setText(pathPrefix);
            Element excludePathPrefixPattern = new Element("excludePathPrefixPattern", namespace);

            int lastLocation = findLastLocation(replication, "checkBinaryExistenceInFilestore", "replicationKey");
            replication.addContent(lastLocation + 1, includePathPrefixPattern);
            replication.addContent(lastLocation + 2, excludePathPrefixPattern);
        }

        log.info("Finished converting of exist replication path prefix field to include/exclude path prefix patterns.");
    }

    @NonNull
    private List<Element> getLocalAndRemoteReplicationsElements(Element rootElement, Namespace namespace) {
        Element localReplications = rootElement.getChild("localReplications", namespace);
        Element remoteReplications = rootElement.getChild("remoteReplications", namespace);
        List<Element> replications = new ArrayList<>();
        if (localReplications != null) {
            List<Element> children = localReplications.getChildren("localReplication", namespace);
            if (CollectionUtils.isNotEmpty(children)) {
                replications.addAll(children);
            }
        }
        if (remoteReplications != null) {
            List<Element> children = remoteReplications.getChildren("remoteReplication", namespace);
            if (CollectionUtils.isNotEmpty(children)) {
                replications.addAll(children);
            }
        }
        return replications;
    }
}
