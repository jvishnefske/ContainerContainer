package org.artifactory.mime.version.converter.v15;

import org.artifactory.mime.MimeType;
import org.artifactory.mime.MimeTypeBuilder;
import org.artifactory.mime.version.converter.MimeTypeConverterBase;
import org.jdom2.Document;

/**
 * Adding Hashicorp HCL file, Used by Terraform (.tf) as viewable text to our mime-types
 *
 * @author BarakH
 */
public class TerraformMimeTypeConverter extends MimeTypeConverterBase {
    @Override
    public void convert(Document doc) {
        MimeType tf = new MimeTypeBuilder("text/plain+tf").extensions("tf").index(false).viewable(true)
                .syntax("plain").build();
        addIfNotExist(doc, tf);
    }

}
