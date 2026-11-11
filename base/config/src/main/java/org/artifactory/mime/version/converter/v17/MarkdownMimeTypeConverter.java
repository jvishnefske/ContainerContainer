package org.artifactory.mime.version.converter.v17;

import org.artifactory.mime.MimeType;
import org.artifactory.mime.MimeTypeBuilder;
import org.artifactory.mime.version.converter.MimeTypeConverterBase;
import org.jdom2.Document;

/**
 * @author Igor Usenko
 */
public class MarkdownMimeTypeConverter extends MimeTypeConverterBase {

    @Override
    public void convert(Document doc) {
        MimeType md = new MimeTypeBuilder("text/plain+md").extensions("md").index(false).viewable(true)
                .syntax("plain").build();
        addIfNotExist(doc, md);
    }

}
