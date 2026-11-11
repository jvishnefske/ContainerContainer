/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.version.converter.v218;

import org.apache.commons.io.FileUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Tamir Hadad
 */
public class BackupSettingConvertTest extends XmlConverterTest {

    private String CONFIG_XML = "/config/test/config.2.1.5.with_malformed_conan_distribution_rule.xml";

    @Test
    public void convert() throws Exception {
        assertConfig(CONFIG_XML);
    }

    private void assertConfig(String xmlPath) throws Exception {
        Document document = convertXml(xmlPath, new BackupSettingConvert());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        validateConfig(rootElement.getChild("backups", namespace), namespace);
    }

    private void validateConfig(Element backups, Namespace namespace) throws Exception {
        assertNotNull(backups, "Missing backups element");
        Element backup = backups.getChild("backup", namespace);
        assertNotNull(backup, "Missing backup element");
        assertNull(backup.getChild("excludeBuilds", backups.getNamespace()));
        validateMarkerFileContent();
    }

    private void validateMarkerFileContent() throws IOException {
        File markerFile = ArtifactoryHome.get().getCreateBackupExcludedBuildNames();
        assertTrue(markerFile.exists(), "Marker file should always exist");
        String fileContent = FileUtils.readFileToString(markerFile);
        fileContent = fileContent.replace("[", "").replace("]", "");
        assertEquals(fileContent, "backup-weekly");
        // Cleanup
        assertTrue(markerFile.delete());
    }
}
