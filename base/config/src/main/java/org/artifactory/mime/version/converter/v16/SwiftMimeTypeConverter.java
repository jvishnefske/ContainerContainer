package org.artifactory.mime.version.converter.v16;

import org.artifactory.mime.MimeType;
import org.artifactory.mime.MimeTypeBuilder;
import org.artifactory.mime.version.converter.MimeTypeConverterBase;
import org.jdom2.Document;

/**
 * Adding Package.swift mimetype
 *
 * @author NadavY
 */
public class SwiftMimeTypeConverter extends MimeTypeConverterBase {
    @Override
    public void convert(Document doc) {
        MimeType swift = new MimeTypeBuilder("text/x-swift").extensions("swift").index(false).viewable(true)
                .syntax("plain").build();
        addIfNotExist(doc, swift);
    }
}
