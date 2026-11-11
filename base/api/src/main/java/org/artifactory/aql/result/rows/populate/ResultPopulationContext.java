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

package org.artifactory.aql.result.rows.populate;

import com.google.common.collect.Maps;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.aql.model.AqlSqlFunction;
import org.artifactory.aql.model.DomainSensitiveField;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Yinon Avraham
 */
public class ResultPopulationContext {

    private final ResultSet resultSet;
    private final List<DomainSensitiveField> resultFields;
    private final AqlSqlFunction sqlFunction;
    private final AqlRepoProvider repoProvider;
    private final Map<String, List<String>> virtualRepoKeysContainingRepoCache = Maps.newHashMap();
    public ResultPopulationContext(ResultSet resultSet, List<DomainSensitiveField> resultFields,
            AqlSqlFunction sqlFunction, AqlRepoProvider repoProvider) {
        this.resultFields = resultFields.stream().filter(f -> !f.isHideField()).toList();
        this.resultSet = resultSet;
        this.sqlFunction = sqlFunction;
        this.repoProvider = repoProvider;
    }

    public ResultSet getResultSet() {
        return resultSet;
    }

    public List<DomainSensitiveField> getResultFields() {
        return Collections.unmodifiableList(resultFields);
    }

    public AqlSqlFunction getSqlFunction() {
        return sqlFunction;
    }

    public AqlRepoProvider getRepoProvider() {
        return repoProvider;
    }

    public List<String> getVirtualRepoKeysContainingRepo(String repoKey) {
        List<String> virtualRepoKeys = virtualRepoKeysContainingRepoCache.get(repoKey);
        if (virtualRepoKeys == null) {
            virtualRepoKeys = repoProvider.getVirtualRepoKeysContainingRepo(repoKey);
            virtualRepoKeysContainingRepoCache.put(repoKey, virtualRepoKeys);
        }
        return virtualRepoKeys;
    }
}
