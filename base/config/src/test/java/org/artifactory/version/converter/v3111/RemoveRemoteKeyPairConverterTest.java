package org.artifactory.version.converter.v3111;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNull;

/**
 * @author Omri Ziv
 */
public class RemoveRemoteKeyPairConverterTest extends XmlConverterTest {

    @Test
    public void testRemovePrimaryKeyPairFromRemote() throws Exception {
        Document doc = convertXml("/config/test/config.3.1.10_remote_repo_with_key_pair.xml",
                new RemoveKeyPairFromRemoteRepositoriesConverter());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element debRemote1 = findRemoteRepoByKey(rootElement, namespace, "deb-remote-1");
        Element debRemote2 = findRemoteRepoByKey(rootElement, namespace, "deb-remote-2");
        assertNull(debRemote1.getChild("primaryKeyPairRef", namespace));
        assertNull(debRemote1.getChild("secondaryKeyPairRef", namespace));
        assertNull(debRemote2.getChild("primaryKeyPairRef", namespace));
        assertNull(debRemote2.getChild("secondaryKeyPairRef", namespace));

    }

    private Element findRemoteRepoByKey(Element rootElement, Namespace namespace, String repoKey) {
        return findRepoByKey(rootElement.getChild("remoteRepositories", namespace)
                , namespace, repoKey);
    }

    private Element findRepoByKey(Element repositoriesElement, Namespace namespace, String repoKey) {
        return repositoriesElement
                .getChildren()
                .stream()
                .filter(element -> element.getChild("key", namespace).getText().equals(repoKey))
                .findFirst().get();
    }
}
