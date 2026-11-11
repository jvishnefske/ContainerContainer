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

package org.artifactory.sapi.common;

import java.io.File;
import java.util.Date;

/**
 * Date: 8/4/11
 * Time: 2:15 PM
 *
 * @author Fred Simon
 */
public interface ExportSettings extends BaseSettings {

    String INVALID_EXPORT_DIR = "Invalid Export Directory";

    String EXPORT_PATH_NOT_IN_ALLOWLIST = "Export path is not within the configured allowed paths";

    boolean isIgnoreRepositoryFilteringRulesOn();

    void setIgnoreRepositoryFilteringRulesOn(boolean ignoreRepositoryFilteringRulesOn);

    boolean isCreateArchive();

    void setCreateArchive(boolean createArchive);

    Date getTime();

    void setTime(Date time);

    boolean isIncremental();

    void setIncremental(boolean incremental);

    boolean isM2Compatible();

    void setM2Compatible(boolean m2Compatible);

    void addCallback(FileExportCallback callback);

    void executeCallbacks(FileExportInfo info, FileExportEvent event);

    void cleanCallbacks();

    boolean isExcludeBuilds();

    void setExcludeBuilds(boolean excludeBuilds);

    boolean isExcludeArtifactBundles();

    boolean isSafeExport();

    void setExcludeArtifactBundles(boolean excludeArtifactBundles);

    /**
     * @return The location of the backup. This can be a folder or a file in case of an archive backup.
     */
    File getOutputFile();

    /**
     * Sets the location of the backup. This can be a folder or a file in case of an archive backup.
     */
    void setOutputFile(File outputFile);
}
