/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.mime.version.converter.v14;

import org.artifactory.mime.MimeType;
import org.artifactory.mime.MimeTypeBuilder;
import org.artifactory.mime.version.converter.MimeTypeConverterBase;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

/**
 * Adds ddeb to the list of supported debian extensions
 *
 * @author Michael Akushsky
 */
public class DebianDebugMimeTypeConverter extends MimeTypeConverterBase {
    @Override
    public void convert(Document doc) {
        MimeType json = new MimeTypeBuilder("application/x-debian-package").extensions("deb", "ddeb").css("deb")
                .build();
        addIfNotExist(doc, json);

        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        for (Object mimetype : rootElement.getChildren("mimetype", namespace)) {
            Element mimeTypeElement = (Element) mimetype;
            if ("application/x-debian-package".equals(mimeTypeElement.getAttributeValue("type", namespace))) {
                mimeTypeElement.getAttribute("extensions").setValue("deb, ddeb");
            }
        }
    }
}
