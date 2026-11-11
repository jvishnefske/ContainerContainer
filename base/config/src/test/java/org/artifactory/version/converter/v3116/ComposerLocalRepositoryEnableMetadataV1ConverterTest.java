package org.artifactory.version.converter.v3116;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import static org.artifactory.descriptor.repo.RepoType.Composer;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class ComposerLocalRepositoryEnableMetadataV1ConverterTest extends XmlConverterTest {

    @Test
    public void testConvert() throws Exception {
        Document doc = convertXml("/config/test/config.3.1.17_convert_composer_local_repositories.xml",
                new ComposerLocalRepositoryEnableMetadataV1Converter());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element localRepositories = rootElement.getChild("localRepositories", namespace);
        for (Element localRepo : localRepositories.getChildren()) {
            Element pkgTypeElement = localRepo.getChild("type", namespace);
            if (pkgTypeElement != null) {
                String pkgType = pkgTypeElement.getText();
                if (StringUtils.equals(pkgType, Composer.getType())) {
                    assertNotNull(localRepo.getChild("enableComposerV1Indexing", namespace));
                    assertEquals(localRepo.getChild("enableComposerV1Indexing", namespace).getText(), "true");
                }
            }
        }
    }

}