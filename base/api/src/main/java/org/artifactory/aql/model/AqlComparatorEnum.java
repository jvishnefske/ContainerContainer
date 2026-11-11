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

package org.artifactory.aql.model;

import lombok.Getter;

/**
 * @author Gidi Shabat
 */
@Getter
public enum AqlComparatorEnum {
    notEquals("$ne"),
    equals("$eq"),
    equalsIgnoreCase("$eqic"),
    // Exact prop_key match, case-folded prop_value: renders "prop_key = ? AND LOWER(prop_value) = ?".
    // Unlike equalsIgnoreCase (which lowers both operands), this keeps the key sargable so an index on
    // prop_key can be used; only meaningful for property (key+value) criteria.
    equalsValueIgnoreCase("$eqvic"),
    greaterEquals("$gte"),
    greater("$gt"),
    matches("$match"),
    matchesIgnoreCase("$matchic"),
    notMatches("$nmatch"),
    lessEquals("$lte"),
    less("$lt");

    public final String signature;

    AqlComparatorEnum(String signature) {
        this.signature = signature;
    }

    public static AqlComparatorEnum value(String comparator) {
        comparator = comparator.toLowerCase();
        for (AqlComparatorEnum comparatorEnum : values()) {
            if (comparatorEnum.getSignature().equals(comparator)) {
                return comparatorEnum;
            }
        }
        return null;
    }

    public boolean isNegative() {
        return this == notMatches || this == notEquals;

    }
}
