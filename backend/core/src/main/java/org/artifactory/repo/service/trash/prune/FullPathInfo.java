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

package org.artifactory.repo.service.trash.prune;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.rows.RowResult;

/**
 * @author Gidi Shabat
 */
@Data
@NoArgsConstructor
public class FullPathInfo implements RowResult {
    private String repo;
    private String path;
    private String name;
    private boolean file;
    private String propertyKey;
    private String propertyValue;
    private int dept;

    @Override
    public void put(DomainSensitiveField field, Object value) {
        if (field.getField() == AqlPhysicalFieldEnum.itemRepo) {
            repo = (String) value;
        } else if (field.getField() == AqlPhysicalFieldEnum.itemName) {
            name = (String) value;
        } else if (field.getField() == AqlPhysicalFieldEnum.itemPath) {
            path = (String) value;
        } else if (field.getField() == AqlPhysicalFieldEnum.itemDepth) {
            dept = (int) value;
        } else if (field.getField() == AqlPhysicalFieldEnum.itemType) {
            file = value == AqlItemTypeEnum.file;
        } else if (field.getField() == AqlPhysicalFieldEnum.propertyKey) {
            propertyKey = (String) value;
        } else if (field.getField() == AqlPhysicalFieldEnum.propertyValue) {
            propertyValue = (String) value;
        } else {
            throw new RuntimeException("Unexpected field for FullPathInfo.class.");
        }
    }

    @Override
    public Object get(DomainSensitiveField field) {
        return null;
    }
}