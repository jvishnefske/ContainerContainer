package org.artifactory.version.converter.v3120;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReplicationPathPrefixConverterTest extends XmlConverterTest {

    @Test
    void convertReplicationPathPrefix() throws Exception {
        Document doc = convertXml("/config/test/config.3.1.21_convert_replication_path_prefix.xml",
                new ReplicationPathPrefixConverter());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element debReplication1 = findLocalReplicationByKey(rootElement, namespace, "deb-local_https___tec4c368c15c");
        Element debReplication2 = findLocalReplicationByKey(rootElement, namespace, "deb-local_https___ted0b07f6ed5");
        assertNull(debReplication1.getChild("pathPrefix", namespace));
        assertNull(debReplication2.getChild("pathPrefix", namespace));
        assertNotNull(debReplication1.getChild("includePathPrefixPattern", namespace));
        assertEquals("", debReplication1.getChild("includePathPrefixPattern", namespace).getText());
        assertNotNull(debReplication1.getChild("excludePathPrefixPattern", namespace));
        assertEquals("", debReplication1.getChild("excludePathPrefixPattern", namespace).getText());
        assertNotNull(debReplication2.getChild("includePathPrefixPattern", namespace));
        assertEquals("/a/*.ddeb", debReplication2.getChild("includePathPrefixPattern", namespace).getText());
        assertNotNull(debReplication2.getChild("excludePathPrefixPattern", namespace));
        assertEquals("", debReplication2.getChild("excludePathPrefixPattern", namespace).getText());
    }

    private Element findLocalReplicationByKey(Element rootElement, Namespace namespace, String replicationKey) {
        return findReplicationByKey(rootElement.getChild("localReplications", namespace)
                , namespace, replicationKey);
    }

    private Element findReplicationByKey(Element replicationElement, Namespace namespace, String replicationKey) {
        return replicationElement
                .getChildren()
                .stream()
                .filter(element -> element.getChild("replicationKey", namespace).getText().equals(replicationKey))
                .findFirst().get();
    }
}