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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import org.artifactory.aql.AqlException;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.common.ConstantValues;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.Row;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Gidi Shabat
 * The class represent single row that returns from the database. in addition the InflatableRow contains the domin
 * sensitive
 * fields that corelates the results and allows to inflate the flate row result into multi domain model
 */
public class InflatableRow implements RowResult {
    private Map<DomainSensitiveField, Object> map = Maps.newHashMap();

    @VisibleForTesting
    int lastInflateResolveIdCallCount;

    /**
     * pushes the pair: {DomainSensitiveField and results} to the InflatableRow
     */
    @Override
    public void put(DomainSensitiveField field, Object value) {
        map.put(field, value);
    }

    @Override
    public Object get(DomainSensitiveField field) {
        return map.get(field);
    }

    /**
     * The method transforms the flat row into multi domain model
     * The result is multi layer map (each layer represent single domain)
     */
    public Map<String, Row> inflate() {
        lastInflateResolveIdCallCount = 0;
        Map<String, Row> result = Maps.newHashMap();
        Row prev = null;
        Map<AqlDomainEnum, String> cachedIds = new EnumMap<>(AqlDomainEnum.class);
        // For each field in the row resolve the domains list and set the value in its location in the multi layer
        //domain
        for (DomainSensitiveField field : map.keySet()) {
            Map<String, Row> current = result;
            List<AqlDomainEnum> subDomains = field.getSubDomains();
            for (AqlDomainEnum subDomain : subDomains) {
                String id = cachedIds.computeIfAbsent(subDomain, this::resolveId);
                // if map exist use it else create it
                if (current == null) {
                    current = Maps.newHashMap();
                    prev.subDomains = current;
                }
                // if element row exist, use it,else create it
                Row row = current.get(id);
                if (row == null) {
                    row = new Row(subDomain);
                    current.put(id, row);
                    fillRelevantFields(row, subDomain);
                }
                prev = row;
                current = row.subDomains;
            }
        }
        // Clean empty rows
        clean(result.values().iterator().next());
        return result;
    }

    /**
     * Cleans all empty row (no need for them)
     */
    private boolean clean(Row next) {
        // Clean all sub-domains
        if (next.subDomains != null) {
            Iterator<Row> iterator = next.subDomains.values().iterator();
            while (iterator.hasNext()) {
                Row row = iterator.next();
                if (clean(row)) {
                    iterator.remove();
                }
            }
        }
        // Check if we should clean this row parent
        try {
            //Special behaviour for archive domain, since archive domain is built by two tables and doesn't
            //have real key
            if (ConstantValues.inflateRowEnhancementEnabled.getBoolean()) {
                for (Field field : Row.getCleanCheckFields()) {
                    if (hasNonIdData(next, field)) {
                        return false;
                    }
                }
            } else {
                Field[] declaredFields = next.getClass().getFields();
                for (Field declaredField : declaredFields) {
                    if (!declaredField.getName().equals("subDomains") && !declaredField.getName().equals("domain")) {
                        if (hasNonIdData(next, declaredField)) {
                            return false;
                        }
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new AqlException("Failed to map result fields", e);
        }
        return next.subDomains == null || next.subDomains.isEmpty();
    }

    private static boolean hasNonIdData(Row next, Field field) throws IllegalAccessException {
        Object value = field.get(next);
        if (value != null && !"archiveId".equals(field.getName())) {
            try {
                if (!AqlPhysicalFieldEnum.valueOf(field.getName()).isId()) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                return true;
            }
        }
        return false;
    }

    /**
     * The method fills all data (result from db) that is relevant for the current domain
     */
    private boolean fillRelevantFields(Row row, AqlDomainEnum subDomain) {
        boolean containsData = false;
        for (Map.Entry<DomainSensitiveField, Object> entry : map.entrySet()) {
            DomainSensitiveField field = entry.getKey();
            Object value = entry.getValue();
            if (field.getField().getDomain() == subDomain) {
                if (value != null && !field.getField().isId()) {
                    containsData = true;
                }

                row.put(field.getField().getName(), value);
            }
        }
        return containsData;
    }

    /**
     * The method returns the element id according to the domain
     */
    private String resolveId(AqlDomainEnum domain) {
        lastInflateResolveIdCallCount++;
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<DomainSensitiveField, Object> entry : map.entrySet()) {
            DomainSensitiveField domainSensitiveField = entry.getKey();
            if (domainSensitiveField.getField().getDomain() == domain) {
                String elementKey = domainSensitiveField.getField().getName();
                Object value = entry.getValue();
                builder.append("((key:").append(elementKey).append(")(value:").append(toString(value)).append("))");
            }
        }
        return builder.toString();
    }

    private String toString(Object value) {
        if (value != null && value.getClass().isArray()) {
            return Arrays.toString((Object[]) value);
        } else {
            return String.valueOf(value);
        }
    }

}
