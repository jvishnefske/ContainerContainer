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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Tamir Hadad
 */
public class BackupSettingConvert implements XmlConverter {
    private static final Logger log = LoggerFactory.getLogger(BackupSettingConvert.class);

    @Override
    public void convert(Document doc) {
        String excludeBuilds = "excludeBuilds";
        log.info("Starting 'backup excludeBuild removal' conversion");
        Element rootElement = doc.getRootElement();
        Namespace ns = rootElement.getNamespace();
        Element backup = rootElement.getChild("backups", ns);
        List<String> backupsExcludedBuilds = new ArrayList<>();
        if ((backup != null) && CollectionUtils.isNotEmpty(backup.getChildren())) {
            for (Element child : backup.getChildren()) {
                if (child != null) {
                    Element excludeChild = child.getChild(excludeBuilds, ns);
                    if (excludeChild == null) {
                        continue;
                    }
                    boolean isExclude = Boolean.parseBoolean(excludeChild.getValue());
                    if (isExclude) {
                        backupsExcludedBuilds.add(child.getChild("key", ns).getText());
                    }
                    child.removeChild(excludeBuilds, ns);
                }
            }
        }
        // The marker file is handled by BuildBackupConverter
        // We need to write the excluded builds backup because we don't know the build-info repo name.
        createMarkerFile(backupsExcludedBuilds);
        log.info("Finished 'backup excludeBuild removal' conversion");
    }

    private void createMarkerFile(List<String> backupsExcludedBuilds) {
        log.debug("need to convert the following backups: {}", backupsExcludedBuilds);

        if (CollectionUtils.isEmpty(backupsExcludedBuilds)) {
            log.debug("skip converter, no backups were found");
            return;
        }
        File markerFile = ArtifactoryHome.get().getCreateBackupExcludedBuildNames();
        try {
            FileUtils.write(markerFile, backupsExcludedBuilds.toString());
            log.debug("backup build exclusion marker file created.");
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't write backup build exclusion marker file", e);
        }
    }

}
