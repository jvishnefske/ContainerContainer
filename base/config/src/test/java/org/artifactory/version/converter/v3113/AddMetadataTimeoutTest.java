package org.artifactory.version.converter.v3113;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

/**
 * @author Alexei Vainshtein
 */
public class AddMetadataTimeoutTest extends XmlConverterTest {

    @Test
    public void testConvert() throws Exception {
        Document doc = convertXml("/config/test/config.3.1.13_remote_with_low_retrieval.xml",
                new AddMetadataTimeout());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element remoteRepositories = rootElement.getChild("remoteRepositories", namespace);
        for (Element remoteRepo : remoteRepositories.getChildren()) {
            Element metadataRetrievalTimeoutSecsElement =
                    remoteRepo.getChild("metadataRetrievalTimeoutSecs", namespace);
            Element retrievalCachePeriodSecsElement = remoteRepo.getChild("retrievalCachePeriodSecs", namespace);
            if (metadataRetrievalTimeoutSecsElement == null && retrievalCachePeriodSecsElement == null) {
                continue;
            }

            assert retrievalCachePeriodSecsElement != null;
            long retrievalCachePeriodSecs = Long.parseLong(retrievalCachePeriodSecsElement.getText());

            if (retrievalCachePeriodSecs >= 60) {
                assertNull(metadataRetrievalTimeoutSecsElement);
                continue;
            }

            if (metadataRetrievalTimeoutSecsElement == null) {
                fail("Wrong value in the metadataRetrievalTimeoutSecs. Expected to be not null but found null");
            }
            String metadataRetrievalTimeoutSecs = metadataRetrievalTimeoutSecsElement.getText();
            assertEquals(Long.parseLong(metadataRetrievalTimeoutSecs), retrievalCachePeriodSecs);
        }
    }

    @Test
    public void testConvertWithExistingMetadata() throws Exception {
        Document doc = convertXml("/config/test/config.3.1.13_remote_with_metadata_timeout_exists.xml",
                new AddMetadataTimeout());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element remoteRepositories = rootElement.getChild("remoteRepositories", namespace);
        for (Element remoteRepo : remoteRepositories.getChildren()) {
            List<Element> children = remoteRepo.getChildren();
            AtomicInteger foundMetadataChildren = new AtomicInteger();
            children.forEach(element -> {
                if (element.getName().equals("metadataRetrievalTimeoutSecs")) {
                    foundMetadataChildren.addAndGet(1);
                }
            });

            if (foundMetadataChildren.get() > 1) {
                fail("Expected to find only one element of metadataRetrievalTimeoutSecs, found " +
                        foundMetadataChildren.get());
            }
            Element metadataRetrievalTimeoutSecsElement =
                    remoteRepo.getChild("metadataRetrievalTimeoutSecs", namespace);
            String metadataRetrievalTimeoutSecs = metadataRetrievalTimeoutSecsElement.getText();
            assertEquals(Long.parseLong(metadataRetrievalTimeoutSecs), 6);
        }
    }
}