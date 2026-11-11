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

package org.artifactory.aql.result.rows;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.fs.FileInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;

/**
 * Item row that's fully mappable to a {@link FileInfo}
 *
 * @author Dan Feldman
 */
@Data
@EqualsAndHashCode(callSuper=false)
public class FileInfoWithStatisticsItemRow extends FileInfoItemRow {

    private long statDownloaded;

    @Override
    public void put(DomainSensitiveField field, Object value) {
        super.put(field, value);
        if (field.getField() == AqlPhysicalFieldEnum.statDownloads) {
            statDownloaded = ((Integer) value).longValue();
        }
    }

    @Override
    public Object get(DomainSensitiveField field) {
        return null;
    }

    @Override
    public RepoPath getRepoPath() {
        if (StringUtils.equals(getPath(), ".")) {
            return RepoPathFactory.create(getRepo(), getName());
        } else {
            return RepoPathFactory.create(getRepo(), getPath() + "/" + getName());
        }
    }
}
