package org.artifactory.mime.version.converter.v16;

import org.artifactory.mime.version.converter.MimeTypeConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Assertions;
import org.testng.annotations.Test;

import java.util.List;

public class SwiftMimeTypeConverterTest extends MimeTypeConverterTest {

    @Test
    public void testConvert() throws Exception {
        Document document =
                convertXml("/org/artifactory/mime/version/mimetypes-v13.xml", new SwiftMimeTypeConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        List<Element> mimetypes = rootElement.getChildren("mimetype", namespace);

        Element tfType = getType(mimetypes, namespace, "text/x-swift");
        Assertions.assertNotNull(tfType);
        Assertions.assertEquals("swift", tfType.getAttributeValue("extensions"));
        Assertions.assertEquals("plain", tfType.getAttributeValue("syntax"));
        Assertions.assertFalse(Boolean.parseBoolean(tfType.getAttributeValue("archive")));
        Assertions.assertFalse(Boolean.parseBoolean(tfType.getAttributeValue("index")));
        Assertions.assertTrue(Boolean.parseBoolean(tfType.getAttributeValue("viewable")));

    }

}