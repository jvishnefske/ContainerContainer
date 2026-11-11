package org.artifactory.version.converter.v3116;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.artifactory.descriptor.repo.RepoType.Composer;

/**
 * This converter will update exist Composer local repositories to keep creating metadata V1 index files.
 *
 * @author BarakH.
 */
public class ComposerLocalRepositoryEnableMetadataV1Converter implements XmlConverter {

    private static final Logger log = LoggerFactory.getLogger(ComposerLocalRepositoryEnableMetadataV1Converter.class);

    @Override
    public void convert(Document doc) {
        log.info("Starting conversion of Composer local repos to keep metadata v1 indexing");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element localRepositories = rootElement.getChild("localRepositories", namespace);
        if (localRepositories != null) {
            for (Element localRepo : localRepositories.getChildren()) {
                Element pkgTypeElement = localRepo.getChild("type", namespace);
                if (pkgTypeElement != null) {
                    String pkgType = pkgTypeElement.getText();
                    if (StringUtils.equals(pkgType, Composer.getType()) &&
                            localRepo.getChild("enableComposerV1Indexing", namespace) == null) {
                        Element enableComposerV1Indexing = new Element("enableComposerV1Indexing", namespace);
                        enableComposerV1Indexing.setText("true");
                        localRepo.addContent(enableComposerV1Indexing);
                        log.info("Enabling Composer metadata V1 indexing for {} repository",
                                localRepo.getChild("key", namespace).getText());
                    }
                }
            }
        }
        log.info("Finished converting Composer local repos to keep metadata v1 indexing");
    }
}
