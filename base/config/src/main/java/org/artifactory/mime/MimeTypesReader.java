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

package org.artifactory.mime;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.artifactory.mime.version.MimeTypesVersion;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

/**
 * A reader of mimetypes configuration file.
 *
 * @author Yossi Shaul
 */
@Slf4j
public class MimeTypesReader {

    public MimeTypes read(File mimeTypesFile) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(mimeTypesFile);
            return read(fis);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("File not found: " + e.getMessage());
        } catch (JAXBException e) {
            throw new RuntimeException("Error reading mimetypes XML: " + e.getMessage());
        } finally {
            IOUtils.closeQuietly(fis);
        }
    }

    public MimeTypes read(InputStream is) throws JAXBException {
        String xmlContent;
        try {
            xmlContent = IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file content: " + e.getMessage(), e);
        }

        return read(xmlContent);
    }

    public MimeTypes read(String xmlContent) throws JAXBException {
        MimeTypesVersion mimeTypesVersion = MimeTypesVersion.findVersion(xmlContent);
        if (!mimeTypesVersion.isCurrent()) {
            log.info("Converting mimetypes.xml version from '{}' to '{}'", mimeTypesVersion,
                    MimeTypesVersion.getCurrent());
            xmlContent = mimeTypesVersion.convert(xmlContent);
        }
        JAXBContext context = JAXBContext.newInstance(MimeType.class, MimeTypes.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        StringReader reader = new StringReader(xmlContent);
        return (MimeTypes) unmarshaller.unmarshal(reader);
    }

}
