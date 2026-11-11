package org.artifactory.mime.version.converter.v15;

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
 * @author BarakH
 */
public class TerraformMimeTypeConverterTest extends MimeTypeConverterTest {

    @Test
    public void testConvert() throws Exception {
        Document document =
                convertXml("/org/artifactory/mime/version/mimetypes-v13.xml", new TerraformMimeTypeConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        List<Element> mimetypes = rootElement.getChildren("mimetype", namespace);

        Element tfType = getType(mimetypes, namespace, "text/plain+tf");
        assertNotNull(tfType);
        assertEquals(tfType.getAttributeValue("extensions"), "tf");
        assertFalse(Boolean.parseBoolean(tfType.getAttributeValue("archive")));
        assertFalse(Boolean.parseBoolean(tfType.getAttributeValue("index")));
        assertTrue(Boolean.parseBoolean(tfType.getAttributeValue("viewable")));
    }
}