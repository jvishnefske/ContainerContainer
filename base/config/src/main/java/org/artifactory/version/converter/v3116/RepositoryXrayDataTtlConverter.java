package org.artifactory.version.converter.v3116;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import java.util.Objects;

/**
 * @author Andrey Tashlitsky
 */
@Slf4j
public class RepositoryXrayDataTtlConverter implements XmlConverter {

    @Override
    public void convert(Document doc) {
        log.info("Starting 'Repository Xray Data TTL' conversion");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        addXrayDataTtlToRepository(rootElement, namespace, "localRepositories");
        addXrayDataTtlToRepository(rootElement, namespace, "remoteRepositories");
        addXrayDataTtlToRepository(rootElement, namespace, "federatedRepositories");
        log.info("Finished 'Repository Xray Data TTL' conversion successfully");
    }

    private void addXrayDataTtlToRepository(Element rootElement, Namespace namespace, String elementName) {
        Element element = rootElement.getChild(elementName, namespace);
        if (element != null) {
            element.getChildren().stream()
                    .map(repositoryElement -> repositoryElement.getChild("xray", namespace))
                    .filter(Objects::nonNull)
                    .forEach(xrayElement -> {
                        Element ttl = xrayElement.getChild("dataTtl", namespace);
                        if (ttl != null) {
                            xrayElement.removeContent(ttl);
                        }
                        xrayElement.addContent(new Element("dataTtl", namespace).setText("0"));
                    });
        }
    }
}
