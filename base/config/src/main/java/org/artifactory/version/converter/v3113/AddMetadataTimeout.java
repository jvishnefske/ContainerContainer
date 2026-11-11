package org.artifactory.version.converter.v3113;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Alexei Vainshtein
 */
public class AddMetadataTimeout implements XmlConverter {

    private static final Logger log = LoggerFactory.getLogger(AddMetadataTimeout.class);

    @Override
    public void convert(Document doc) {
        log.info("Starting conversion of remote repos default metadata timeout");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element remoteRepositories = rootElement.getChild("remoteRepositories", namespace);
        if (remoteRepositories != null) {
            for (Element remoteRepo : remoteRepositories.getChildren()) {
                if (remoteRepo.getChild("metadataRetrievalTimeoutSecs", namespace) != null) {
                    log.debug("Found \"metadataRetrievalTimeoutSecs\" in the config, skipping conversion of the repo");
                    continue;
                }
                Element retrievalCachePeriodSecsElement = remoteRepo.getChild("retrievalCachePeriodSecs", namespace);
                if (retrievalCachePeriodSecsElement != null) {
                    updateIfNeeded(namespace, remoteRepo, retrievalCachePeriodSecsElement);
                }
            }
        }
        log.info("Finished converting of remote repos default metadata timeout");
    }

    private void updateIfNeeded(Namespace namespace, Element remoteRepo, Element retrievalCachePeriodSecsElement) {
        String retrievalCachePeriodSecsElementText = retrievalCachePeriodSecsElement.getText();
        if (StringUtils.isNumeric(retrievalCachePeriodSecsElementText) &&
                Long.parseLong(retrievalCachePeriodSecsElementText) < 60 &&
                Long.parseLong(retrievalCachePeriodSecsElementText) > 0) {
            log.debug(
                    "Found that retrieval cache period is bigger then 0 and less then 60 ({}). Updating " +
                            "the metadata timeout.",
                    retrievalCachePeriodSecsElementText);
            Element metadataRetrievalTimeoutSecs = new Element("metadataRetrievalTimeoutSecs", namespace);
            metadataRetrievalTimeoutSecs.setText(retrievalCachePeriodSecsElementText);
            int retrievalCachePeriodSecs = findLastLocation(remoteRepo, "retrievalCachePeriodSecs");
            remoteRepo.addContent(retrievalCachePeriodSecs + 1, new Text("\n            "));
            remoteRepo.addContent(retrievalCachePeriodSecs + 2, metadataRetrievalTimeoutSecs);
        }
    }
}
