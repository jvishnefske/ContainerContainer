package org.artifactory.mime.version.converter.v17;

import org.artifactory.mime.MimeType;
import org.artifactory.mime.MimeTypeBuilder;
import org.artifactory.mime.version.converter.MimeTypeConverterBase;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import java.util.List;

/**
 * Adds the xz MIME type entry if missing, or updates it to include nar.xz if the entry
 * already exists (e.g., from a fresh install of RTDEV-51561 which added the entry to the
 * default XML but without a converter for upgrades).
 *
 * @author Linoy Levi
 */
public class XzMimeTypeConverter extends MimeTypeConverterBase {

    private static final String XZ_MIME_TYPE = "application/x-xz";
    private static final String FULL_EXTENSIONS = "xz, tar.xz, nar.xz";

    @Override
    public void convert(Document doc) {
        Element existing = findByType(doc);
        if (existing != null) {
            existing.setAttribute("extensions", FULL_EXTENSIONS);
        } else {
            MimeType xz =
                    new MimeTypeBuilder(XZ_MIME_TYPE).extensions(FULL_EXTENSIONS).archive(true).index(true).build();
            addIfNotExist(doc, xz);
        }
    }

    private Element findByType(Document doc) {
        Element root = doc.getRootElement();
        Namespace namespace = root.getNamespace();
        List<Element> mimetypes = root.getChildren("mimetype", namespace);
        for (Element mimeTypeElement : mimetypes) {
            if (XZ_MIME_TYPE.equals(mimeTypeElement.getAttributeValue("type", namespace))) {
                return mimeTypeElement;
            }
        }
        return null;
    }
}
