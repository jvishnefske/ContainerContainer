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

package org.artifactory.storage.db.aql.sql.builder.query.aql;

import com.google.common.collect.Lists;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlSortTypeEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains the sort info in the AqlQuery
 *
 * @author Gidi Shabat
 */
public class SortDetails {
    private AqlSortTypeEnum sortType;
    private List<AqlPhysicalFieldEnum> fields = Lists.newArrayList();

    public void addField(AqlPhysicalFieldEnum fieldEnum) {
        this.fields.add(fieldEnum);
    }

    public List<AqlPhysicalFieldEnum> getFields() {
        return this.fields;
    }

    public AqlSortTypeEnum getSortType() {
        return this.sortType;
    }

    public void setSortType(AqlSortTypeEnum sortType) {
        this.sortType = sortType;
    }

    public boolean isEmpty() {
        return this.sortType == null || this.fields.size() == 0;
    }

    public String toNative() {
        StringBuilder builder = new StringBuilder();
        List<String> sortFields = new ArrayList<>();
        builder.append("sort({");
        builder.append("\"").append(this.sortType.getAqlName()).append("\":[");
        for (AqlPhysicalFieldEnum field : this.fields) {
            sortFields.add("\"" + field.getSignature() + "\"");
        }
        builder.append(String.join(",", sortFields));
        builder.append("]})");
        return builder.toString();
    }
}
