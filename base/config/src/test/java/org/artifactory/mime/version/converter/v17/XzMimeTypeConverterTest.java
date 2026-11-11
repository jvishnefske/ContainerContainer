package org.artifactory.mime.version.converter.v17;

import org.artifactory.mime.version.converter.MimeTypeConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Test for {@link XzMimeTypeConverter}.
 *
 * @author Linoy Levi
 */
public class XzMimeTypeConverterTest extends MimeTypeConverterTest {

    @Test
    public void addsXzEntryWhenMissing() throws Exception {
        Document document = convertXml("/org/artifactory/mime/version/mimetypes-v15.xml",
                new XzMimeTypeConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        List mimetypes = rootElement.getChildren("mimetype", namespace);

        Element xzType = getType(mimetypes, namespace, "application/x-xz");
        assertNotNull(xzType);
        assertEquals(xzType.getAttributeValue("extensions"), "xz, tar.xz, nar.xz");
        assertTrue(Boolean.valueOf(xzType.getAttributeValue("archive")));
        assertTrue(Boolean.valueOf(xzType.getAttributeValue("index")));
    }

    @Test
    public void updatesExistingXzExtensions() throws Exception {
        Document document = convertXml("/org/artifactory/mime/version/mimetypes-v16.xml",
                new XzMimeTypeConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        List mimetypes = rootElement.getChildren("mimetype", namespace);

        Element xzType = getType(mimetypes, namespace, "application/x-xz");
        assertNotNull(xzType);
        assertEquals(xzType.getAttributeValue("extensions"), "xz, tar.xz, nar.xz");
        assertTrue(Boolean.valueOf(xzType.getAttributeValue("archive")));
        assertTrue(Boolean.valueOf(xzType.getAttributeValue("index")));
    }

    @Test
    public void idempotentWhenAlreadyCorrect() throws Exception {
        Document document = convertXml("/org/artifactory/mime/version/mimetypes-v16.xml",
                new XzMimeTypeConverter());
        // Run again on the already-converted document
        new XzMimeTypeConverter().convert(document);

        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        List mimetypes = rootElement.getChildren("mimetype", namespace);

        long xzCount = 0;
        for (Object el : mimetypes) {
            Element e = (Element) el;
            if ("application/x-xz".equals(e.getAttributeValue("type", namespace))) {
                xzCount++;
            }
        }
        assertEquals(xzCount, 1, "Should have exactly one xz entry after running converter twice");
        assertEquals(getType(mimetypes, namespace, "application/x-xz")
                .getAttributeValue("extensions"), "xz, tar.xz, nar.xz");
    }
}
