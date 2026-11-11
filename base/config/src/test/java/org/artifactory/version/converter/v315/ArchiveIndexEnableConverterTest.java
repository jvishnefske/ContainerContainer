package org.artifactory.version.converter.v315;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Alexei Vainshtein
 */
public class ArchiveIndexEnableConverterTest extends XmlConverterTest {

    @Test
    public void testConvertWithoutArchiveIndex() throws Exception {
        Document doc = convertXml("/config/test/config.3.1.3_with_basic_auth.xml",
                new ArchiveIndexEnableConverter());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element archiveIndexEnabled = rootElement.getChild("archiveIndexEnabled", namespace);
        Assert.assertNotNull(archiveIndexEnabled);
        Assert.assertEquals(archiveIndexEnabled.getValue(), "true");
    }

    @Test
    public void testConvertWithArchiveIndex() throws Exception {
        Document doc = convertXml("/config/test/config.3.1.5_with-false_archive_index.xml",
                new ArchiveIndexEnableConverter());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element archiveIndexEnabled = rootElement.getChild("archiveIndexEnabled", namespace);
        Assert.assertNotNull(archiveIndexEnabled);
        Assert.assertEquals(archiveIndexEnabled.getValue(), "false");
    }

    @Test
    public void testConvertWithoutOffline() throws Exception {
        Document doc = convertXml("/config/test/config.3.1.5_without-offline.xml",
                new ArchiveIndexEnableConverter());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element archiveIndexEnabled = rootElement.getChild("archiveIndexEnabled", namespace);
        Assert.assertNotNull(archiveIndexEnabled);
        Assert.assertEquals(archiveIndexEnabled.getValue(), "true");
    }

    @Test
    public void testConvertWithServerName() throws Exception {
        Document doc = convertXml("/config/test/config.3.1.5_with-server-name.xml",
                new ArchiveIndexEnableConverter());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element archiveIndexEnabled = rootElement.getChild("archiveIndexEnabled", namespace);
        Assert.assertNotNull(archiveIndexEnabled);
        Assert.assertEquals(archiveIndexEnabled.getValue(), "true");
    }
}