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

import java.util.Date;

import static org.artifactory.aql.model.AqlDomainEnum.statistics;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemActualMd5;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemActualSha1;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemCreated;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemCreatedBy;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemDepth;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemId;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemModified;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemModifiedBy;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemName;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemOriginalMd5;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemOriginalSha1;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemPath;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemRepo;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemSize;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemType;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemUpdated;

/**
 * @author Gidi Shabat
 */
@QueryTypes(value = statistics, physicalFields = {itemId, itemType, itemRepo, itemPath, itemName,
        itemDepth, itemCreated, itemCreatedBy, itemModified, itemModifiedBy, itemUpdated,
        itemSize, itemActualSha1, itemOriginalSha1, itemActualMd5, itemOriginalMd5})
public interface AqlStatisticItem extends AqlItem {

    long getStatId();

    Date getDownloaded();

    int getDownloads();

    String getDownloadedBy();

    Date getRemoteDownloaded();

    int getRemoteDownloads();

    String getRemoteDownloadedBy();

    String getRemoteOrigin();

    String getRemotePath();
}
