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
import lombok.Data;
import org.artifactory.aql.action.AqlAction;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlQueryRepoType;
import org.artifactory.aql.model.AqlSqlFunction;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.rows.AqlRowResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The class contains all the information that has been collected from the AqlApi or the Parser
 *
 * @author Gidi Shabat
 */
@Data
public class AqlQuery<T extends AqlRowResult> {

    private List<DomainSensitiveField> resultFields = Lists.newArrayList();
    private SortDetails sort;
    private List<AqlQueryElement> aqlElements = Lists.newArrayList();
    // We create these parameters in order for us to reverse engineer the query with the toNative function
    // without altering the original aqlElements list, to avoid possible complications
    private List<AqlQueryElement> elementsForNative = Lists.newArrayList();
    private List<AqlQuery<T>> unionSubQueries = new ArrayList<>();
    private List<String> includeForNative = Lists.newArrayList();
    private AqlDomainEnum domain;
    private long limit = Long.MAX_VALUE;
    private long offset = 0;
    private AqlAction action;
    private boolean distinct = true;
    private AqlSqlFunction sqlFunction;
    private TransitiveMetadata transitiveMetadata = new TransitiveMetadata();
    private AqlQueryRepoType queryRepoType = AqlQueryRepoType.local;
    private List<AqlQueryElement> defaultCriteria = new ArrayList<>();
    private List<AqlQueryElement> includedProperties = new ArrayList<>();
    private boolean nullExists = false;

    public void putSubQuery(List<AqlQueryElement> elements) {
        AqlQuery<T> copy = new AqlQuery<>();
        copy.setAqlElements(elements);
        copy.getAqlElements().addAll(this.defaultCriteria);
        unionSubQueries.add(copy);
    }

    public String toNative() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.nativeDomain()).append(this.nativeAction()).append("(");
        if (!this.elementsForNative.isEmpty()) {
            builder.append(this.nativeFilter());
        }
        builder.append(")");
        if (!this.includeForNative.isEmpty()) {
            builder.append(".").append(this.nativeInclude());
        }
        if (this.transitiveMetadata.isTransitive()) {
            builder.append(".").append(this.nativeTransitive());
        }
        if (this.sort != null && !this.sort.isEmpty()) {
            builder.append(".").append(this.nativeSort());
        }
        if (this.offset > 0 && this.offset < Long.MAX_VALUE) {
            builder.append(".").append(this.nativeOffset());
        }
        if (this.limit > 0 && this.limit < Long.MAX_VALUE) {
            builder.append(".").append(this.nativeLimit());
        }
        return builder.toString();
    }

    /**
     * Enlarges the query limit by multiplying it with the scan factor.
     * The result is capped at Long.MAX_VALUE to prevent overflow.
     *
     * @param scanFactor the factor by which to multiply the current limit
     */
    public void enlargeLimit(int scanFactor) {
        if (scanFactor <= 0) {
            return;
        }
        if (limit > Long.MAX_VALUE / scanFactor) {
            limit = Long.MAX_VALUE;
        } else {
            limit = limit * scanFactor;
        }
    }

    private String nativeInclude() {
        return "include(" +
                String.join(", ", this.getIncludeForNative()) + ")";
    }

    private String nativeSort() {
        return this.sort.toNative();
    }

    private String nativeFilter() {
        StringBuilder builder = new StringBuilder();
        for (AqlQueryElement element : this.elementsForNative) {
            builder.append(element.toNative());
        }

        return builder.toString();
    }

    private String nativeDomain() {
        StringBuilder builder = new StringBuilder();
        for (String subDomain : this.domain.subDomains) {
            builder.append(subDomain).append(".");
        }
        return builder.toString();
    }

    private String nativeLimit() {
        return "limit(" + this.limit + ")";
    }

    private String nativeOffset() {
        return "offset(" + this.offset + ")";
    }

    private String nativeAction() {
        return this.action.getName();
    }

    private String nativeTransitive() {
        return "transitive()";
    }
}
