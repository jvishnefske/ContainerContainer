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

package org.artifactory.storage.db.search.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.sapi.search.VfsBoolType;
import org.artifactory.sapi.search.VfsComparatorType;
import org.artifactory.sapi.search.VfsDateFieldName;
import org.artifactory.sapi.search.VfsFunctionType;
import org.artifactory.sapi.search.VfsQueryFieldType;

import java.util.Arrays;
import java.util.Calendar;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Date: 8/6/11
 * Time: 12:01 PM
 *
 * @author Fred Simon
 */
class VfsQueryCriterionDbImpl extends BaseVfsQueryCriterion {
    private final VfsQueryFieldType fieldType;
    private final String propertyName;
    private VfsComparatorType comparator = null;

    @Getter
    @Setter
    private boolean caseInsensitive;
    @Getter
    @Setter
    private String escapeSymbol = "";

    Object value;
    VfsFunctionType function = VfsFunctionType.NONE;

    VfsQueryCriterionDbImpl(String propertyName) {
        super();
        if (StringUtils.isEmpty(propertyName)) {
            throw new IllegalArgumentException("Cannot accept null or empty property name!");
        }
        this.propertyName = getPropertyName(propertyName);
        if ("node_name".equals(propertyName)
                || "node_path".equals(propertyName)
                || VfsDateFieldName.LAST_MODIFIED.getPropName().equals(propertyName)
                || VfsDateFieldName.CREATED.getPropName().equals(propertyName)) {
            fieldType = VfsQueryFieldType.BASE_NODE;
        } else if (VfsDateFieldName.LAST_DOWNLOADED.getPropName().equals(propertyName)) {
            fieldType = VfsQueryFieldType.STATISTIC;
        } else if (VfsDateFieldName.LAST_REMOTE_DOWNLOADED.getPropName().equals(propertyName)) {
            fieldType = VfsQueryFieldType.REMOTE_STATISTIC;
        } else if (propertyName.startsWith(ChecksumType.sha1.name())
                || propertyName.startsWith(ChecksumType.md5.name())) {
            fieldType = VfsQueryFieldType.CHECKSUM;
        } else {
            fieldType = VfsQueryFieldType.PROPERTY;
        }
    }

    /**
     * Retrieves actual name from VfsDateFieldName (if exist)
     *
     * @param propertyName the property name
     * @return actual name of VfsDateFieldName
     */
    private String getPropertyName(String propertyName) {
        VfsDateFieldName vfsDateFieldName = VfsDateFieldName.byPropertyName(propertyName);
        if (vfsDateFieldName != null && vfsDateFieldName.hasPropAlias()) {
            return vfsDateFieldName.getPropActualName();
        }
        return propertyName;
    }

    public VfsComparatorType getComparator() {
        if (comparator == null) {
            if (value == null || StringUtils.isBlank(value.toString())) {
                return VfsComparatorType.ANY;
            } else {
                if (DbQueryHelper.hasWildcards(value.toString())) {
                    return VfsComparatorType.CONTAINS;
                } else {
                    return VfsComparatorType.EQUAL;
                }
            }
        }
        return comparator;
    }

    public void setComparator(@Nonnull VfsComparatorType comparator) {
        this.comparator = comparator;
    }

    public void setValue(Object value) {
        if (value != null && value instanceof String[]) {
            String[] vals = (String[]) value;
            if (vals.length == 0) {
                this.value = null;
            }
            if (vals.length == 1) {
                this.value = vals[0];
            } else {
                this.value = Arrays.asList(vals);
            }
        } else {
            this.value = value;
        }
    }

    public void setFunction(@Nonnull VfsFunctionType function) {
        this.function = function;
    }

    @Override
    public boolean hasPropertyFilter() {
        return fieldType.isProperty();
    }

    @Override
    public boolean hasStatisticFilter() {
        return fieldType.isStatistic() || fieldType.isRemoteStatistic();
    }

    @Override
    public boolean isValid() {
        VfsComparatorType comp = getComparator();
        if (comp == VfsComparatorType.NONE || comp == VfsComparatorType.ANY) {
            // No need for value sor always true
            return true;
        }
        if (value == null ||
                !(value instanceof Calendar || value instanceof Long
                        || value instanceof String || value instanceof Iterable)) {
            // Un-recognized type
            return false;
        }
        return !StringUtils.isEmpty(value.toString());
    }

    protected void fillPropertyName(DbSqlQueryBuilder query) {
        String fieldFullName = propertyName;
        switch (fieldType) {
            case PROPERTY:
                query.appendNodePropsTableName().append(".prop_key = ");
                query.addParam(propertyName);
                query.append("AND ");
                fieldFullName = query.getNodePropsTableName() + ".prop_value";
                break;
            case CHECKSUM:
            case BASE_NODE:
            case PATH:
                fieldFullName = "nodes." + propertyName;
                break;
            case STATISTIC:
                fieldFullName = "stats." + propertyName;
                break;
            case REMOTE_STATISTIC:
                fieldFullName = "stats_remote." + propertyName;
                break;
        }

        if (caseInsensitive) {
            query.append("lower(").append(fieldFullName).append(")");
        } else {
            query.append(fieldFullName);
        }
    }

    @Override
    protected VfsBoolType fill(DbSqlQueryBuilder query) {
        VfsComparatorType comp = getComparator();
        query.addNextBoolIfNeeded();
        if (fieldType == VfsQueryFieldType.PROPERTY) {
            handlePropertyFieldType(query);
        }
        fillPropertyName(query);
        query.appendIfNeeded(' ');
        query.append(comp.str).append(' ');
        appendComparatorAndValue(query, comp);
        if (fieldType == VfsQueryFieldType.PROPERTY) {
            closePropertyFieldType(query);
        }
        return nextBool;
    }

    private void closePropertyFieldType(DbSqlQueryBuilder query) {
        query.append(')');
        if (nextBool == VfsBoolType.OR) {
            query.usedPropertyCriteria = 0;
        }
    }

    private void appendComparatorAndValue(DbSqlQueryBuilder query, VfsComparatorType comp) {
        if (comp.acceptValue()) {
            boolean applyFunction = comp.acceptFunction() && VfsFunctionType.NONE != function;
            if (applyFunction) {
                query.append(function.str).append('(');
            }
            if (isValid()) {
                switch (value) {
                    case String stringValue -> appendStringValue(query, stringValue);
                    case Calendar calendar -> query.addParam((calendar).getTimeInMillis());
                    default -> appendOtherValue(query);
                }
            }
            if (applyFunction) {
                query.append(')');
            }
        }
    }

    private void appendStringValue(DbSqlQueryBuilder query, String stringValue) {
        if (caseInsensitive) {
            query.addParam(valueToLowerCase(DbQueryHelper.convertWildcards(stringValue, escapeSymbol)));
        } else {
            query.addParam(DbQueryHelper.convertWildcards(stringValue, escapeSymbol));
        }
        if (!escapeSymbol.isEmpty()) {
            query.append("ESCAPE '" + escapeSymbol + "'");
        }
    }

    private void appendOtherValue(DbSqlQueryBuilder query) {
        if (caseInsensitive) {
            query.addParam(valueToLowerCase(value));
        } else {
            query.addParam(value);
        }
    }

    private void handlePropertyFieldType(DbSqlQueryBuilder query) {
        query.usedPropertyCriteria++;
        if (query.usedPropertyCriteria > 1) {
            query.append("EXISTS (SELECT ").appendNodePropsTableName().append(".node_id ")
                    .append("FROM node_props ").appendNodePropsTableName()
                    .append(" WHERE nodes.node_id = ").appendNodePropsTableName().append(".node_id")
                    .append(" AND ");
        } else {
            query.append('(');
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private Object valueToLowerCase(@Nullable Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof String) {
            return ((String) value).toLowerCase();
        } else if (value instanceof Iterable) {
            return StreamSupport
                    .stream(((Iterable) value).spliterator(), false)
                    .map(valueItem -> {
                        if (valueItem instanceof String) {
                            return ((String) valueItem).toLowerCase();
                        } else {
                            return valueItem;
                        }
                    })
                    .collect(Collectors.toList());
        } else {
            return value;
        }
    }
}
