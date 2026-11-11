package org.artifactory.version.converter.v345;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Evgeny Kurtser
 */
public class ArchiveIndexDisableConverterTest extends XmlConverterTest {

    @Test
    public void testConvertWithoutArchiveIndex() throws Exception {
        Document doc = convertXml("/config/test/config.3.1.3_with_basic_auth.xml",
                new ArchiveIndexDisableConverter());
        assertArchiveIndexEnabledDoesNotExist(doc);
    }

    @Test
    public void testConvertWithArchiveIndex() throws Exception {
        Document doc = convertXml("/config/test/config.3.1.5_with-false_archive_index.xml",
                new ArchiveIndexDisableConverter());
        assertArchiveIndexEnabledDoesNotExist(doc);
    }

    private void assertArchiveIndexEnabledDoesNotExist(Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element archiveIndexEnabled = rootElement.getChild("archiveIndexEnabled", namespace);
        Assert.assertNull(archiveIndexEnabled);
    }

}