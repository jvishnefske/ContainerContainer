package org.artifactory.mime.version.converter.v17;

import org.artifactory.mime.version.converter.MimeTypeConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Igor Usenko
 */
public class MarkdownMimeTypeConverterTest extends MimeTypeConverterTest {

    @Test
    public void testConvert() throws Exception {
        Document document =
                convertXml("/org/artifactory/mime/version/mimetypes-v17.xml", new MarkdownMimeTypeConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        List<Element> mimetypes = rootElement.getChildren("mimetype", namespace);

        Element mdType = getType(mimetypes, namespace, "text/plain+md");
        assertNotNull(mdType);
        assertEquals(mdType.getAttributeValue("extensions"), "md");
        assertFalse(Boolean.parseBoolean(mdType.getAttributeValue("archive")));
        assertFalse(Boolean.parseBoolean(mdType.getAttributeValue("index")));
        assertTrue(Boolean.parseBoolean(mdType.getAttributeValue("viewable")));
    }
}
