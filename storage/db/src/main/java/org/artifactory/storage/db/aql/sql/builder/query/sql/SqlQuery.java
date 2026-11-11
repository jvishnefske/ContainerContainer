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

package org.artifactory.storage.db.aql.sql.builder.query.sql;

import com.google.common.collect.Lists;
import lombok.Data;
import org.artifactory.aql.action.AqlAction;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlSqlFunction;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.rows.AqlRowResult;

import java.util.List;


/**
 * The class represent Sql query, it also contains some simple convenient methods that helps to build the query
 *
 * @author Gidi Shabat
 */
@Data
public class SqlQuery<T extends AqlRowResult> {

    private String query = "";
    private List<Object> params = Lists.newArrayList();
    private List<DomainSensitiveField> resultFields;
    private long limit;
    private long offset;
    private final AqlDomainEnum domain;
    private AqlAction action;
    private AqlSqlFunction sqlFunction;

    SqlQuery(AqlDomainEnum domain) {
        this.domain = domain;
    }

    public Object[] getQueryParams() {
        Object[] objects = new Object[params.size()];
        return params.toArray(objects);
    }

    public String getQueryString() {
        return query;
    }

    @Override
    public String toString() {
        return "SqlQuery{" +
                "query='" + query + '\'' +
                ", params=" + params +
                '}';
    }
}
