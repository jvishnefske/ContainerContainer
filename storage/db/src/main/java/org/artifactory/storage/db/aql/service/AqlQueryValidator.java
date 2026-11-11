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

package org.artifactory.storage.db.aql.service;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.artifactory.aql.AqlException;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlField;
import org.artifactory.aql.model.AqlFieldEnum;
import org.artifactory.aql.model.AqlFieldEnumSwitch;
import org.artifactory.aql.model.AqlLogicalFieldEnum;
import org.artifactory.aql.model.AqlPermissionProvider;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimpleCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SortDetails;
import org.artifactory.storage.db.aql.sql.builder.query.sql.AqlToSqlQueryBuilderException;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;
import org.artifactory.storage.db.aql.sql.model.SqlTableEnum;

import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.artifactory.aql.model.AqlPhysicalFieldEnum.buildName;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.buildNumber;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.buildRepo;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemName;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemPath;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemRepo;

/**
 * @author Gidi Shabat
 */
public class AqlQueryValidator {

    private static final Function<DomainSensitiveField, AqlPhysicalFieldEnum> toAqlPhysicalFieldEnumOrNull =
            new Function<>() {
                private final AqlFieldEnumSwitch<AqlPhysicalFieldEnum> physicalFieldOrNull =
                        new PhysicalFieldOrNullSwitch();

                @Nullable
                @Override
                public AqlPhysicalFieldEnum apply(@Nullable DomainSensitiveField domainSensitiveField) {
                    if (domainSensitiveField != null) {
                        return domainSensitiveField.getField().doSwitch(physicalFieldOrNull);
                    } else {
                        return null;
                    }
                }
            };

    public <T extends AqlRowResult> void validate(AqlQuery<T> aqlQuery, AqlPermissionProvider permissionProvider) {
        // Assert that all the sort fields are unique
        this.assertSortFieldsAreUnique(aqlQuery);
        // Assert that all the sort fields exist in the result fields
        this.assertSortFieldsInResult(aqlQuery);
        // Assert that the result fields contains the repo, path and name fields for permissions needs
        this.assertMinimalResultFields(aqlQuery, permissionProvider);
        // Assert that the if the result fields contain the virtual_repos field then its required fields are also
        //included
        this.assertRequiredResultFieldsWhenVirtualReposIncluded(aqlQuery);
        // Block property result filter for OSS
        this.blockPropertyResultFilterForOSS(aqlQuery, permissionProvider);
        // Block sorting for OSS
        this.blockSortingForOSS(aqlQuery, permissionProvider);
        // Some actions are not supported on all domains
        this.assertActionSupportedForDomain(aqlQuery);
        // Assert that a transitive query is valid
        this.assertTransitiveValid(aqlQuery);
    }

    private <T extends AqlRowResult> void assertTransitiveValid(AqlQuery<T> aqlQuery) {
        if (aqlQuery.getTransitiveMetadata().isTransitive()) {
            // Blocks offset part for transitive queries
            this.blockOffsetForTransitive(aqlQuery);
            // Blocks sorting part for transitive queries
            this.blockSortingForTransitive(aqlQuery);
            // Makes sure that only valid domains are part of the transitive query
            // This means that the main domain is items, and that the include domain
            // Are either items or properties
            this.assertDomainsValid(aqlQuery);
            // Makes sure that in the find clause, one of them is a repo equals clause
            this.assertRepoEqualsClauseExists(aqlQuery);
            // Makes sure that only a single repo appears in the find clause
            this.assertSingleRepo(aqlQuery);
        }
    }

    private <T extends AqlRowResult> void blockOffsetForTransitive(AqlQuery<T> aqlQuery) {
        if (aqlQuery.getOffset() > 0) {
            throw new AqlException("When including the transitive property, offset is not supported by AQL");
        }
    }

    private <T extends AqlRowResult> void assertDomainsValid(AqlQuery<T> aqlQuery) {
        if (!aqlQuery.getDomain().equals(AqlDomainEnum.items)) {
            throw new AqlException("Only the items domain can be used with the transitive property");
        }

        aqlQuery.getResultFields()
                .forEach(this::validateSubDomains);
    }

    private void validateSubDomains(DomainSensitiveField domain) {
        if (domain.getSubDomains()
                .stream()
                .anyMatch(currDomain -> !currDomain.equals(AqlDomainEnum.items) &&
                        !currDomain.equals(AqlDomainEnum.properties))) {
            throw new AqlException(
                    "Only items and properties subdomains can be used in the include section with the transitive " +
                            "property");
        }
    }

    private <T extends AqlRowResult> void assertRepoEqualsClauseExists(AqlQuery<T> aqlQuery) {
        List<Criterion> criteria = aqlQuery.getAqlElements().stream()
                .filter(curr -> curr instanceof SimpleCriterion)
                .map(curr -> (Criterion) curr)
                .collect(Collectors.toList());
        if (criteria.stream()
                .noneMatch(this::validateRepoCriterion)) {
            throw new AqlException(
                    "When including the transitive property, the repo property has to be specified with the $eq " +
                            "operator");
        }
    }

    private boolean validateRepoCriterion(Criterion criterion) {
        AqlField field = (AqlField) criterion.getVariable1();
        return field.getFieldEnum().getSignature().equals("repo") && criterion.getComparatorName().equals(
                AqlComparatorEnum.equals.getSignature());
    }

    private <T extends AqlRowResult> void assertSingleRepo(AqlQuery<T> aqlQuery) {
        if (!this.isSingleRepo(aqlQuery)) {
            throw new AqlException("When including the transitive property, only a single repo can be specified");
        }
    }

    private <T extends AqlRowResult> boolean isSingleRepo(AqlQuery<T> aqlQuery) {
        return aqlQuery.getAqlElements().stream()
                .filter(curr -> curr instanceof SimpleCriterion)
                .map(curr -> (Criterion) curr)
                .filter(curr -> ((AqlField) curr.getVariable1()).getFieldEnum().getSignature().equals("repo"))
                .collect(Collectors.groupingBy(curr -> ((AqlValue) curr.getVariable2()).toObject()))
                .values()
                .size() == 1;
    }

    private <T extends AqlRowResult> void assertMinimalResultFields(AqlQuery<T> aqlQuery,
            AqlPermissionProvider permissionProvider) {
        if (permissionProvider.isAdmin()) {
            return;
        }
        List<AqlPhysicalFieldEnum> resultFields =
                Lists.transform(aqlQuery.getResultFields(), toAqlPhysicalFieldEnumOrNull);
        if (AqlDomainEnum.items == aqlQuery.getDomain()) {
            List<AqlPhysicalFieldEnum> minimalResultFields = Lists.newArrayList(itemRepo, itemPath, itemName);
            for (AqlPhysicalFieldEnum fieldEnum : minimalResultFields) {
                if (!resultFields.contains(fieldEnum)) {
                    throw new AqlToSqlQueryBuilderException("For permissions reasons AQL items domain demands" +
                            " the following fields: repo, path and name.");
                }
            }
        }
        if (AqlDomainEnum.builds == aqlQuery.getDomain()) {
            List<AqlPhysicalFieldEnum> minimalResultFields = Lists.newArrayList(buildName, buildNumber, buildRepo);
            for (AqlPhysicalFieldEnum fieldEnum : minimalResultFields) {
                if (!resultFields.contains(fieldEnum)) {
                    throw new AqlToSqlQueryBuilderException("For permissions reasons AQL builds domain demands" +
                            " the following fields: name, number and repo.");
                }
            }
        }
    }

    private <T extends AqlRowResult> void assertRequiredResultFieldsWhenVirtualReposIncluded(AqlQuery<T> aqlQuery) {
        if (!AqlDomainEnum.items.equals(aqlQuery.getDomain())) {
            return;
        }
        List<AqlFieldEnum> resultFields = aqlQuery.getResultFields().stream()
                .map(DomainSensitiveField::getField)
                .collect(Collectors.toList());
        if (resultFields.contains(AqlLogicalFieldEnum.itemVirtualRepos)) {
            List<AqlPhysicalFieldEnum> requiredResultFields = Lists.newArrayList(itemRepo, itemPath, itemName);
            for (AqlPhysicalFieldEnum fieldEnum : requiredResultFields) {
                if (!resultFields.contains(fieldEnum)) {
                    throw new AqlToSqlQueryBuilderException("When including virtual_repos field, the following " +
                            "fields are also required: repo, path and name.");
                }
            }
        }
    }

    private <T extends AqlRowResult> void assertSortFieldsAreUnique(AqlQuery<T> aqlQuery) {
        // Assert that all the sort fields are unique
        if (aqlQuery.getSort() != null && aqlQuery.getSort().getFields() != null) {
            List<AqlPhysicalFieldEnum> fields = aqlQuery.getSort().getFields();
            if (fields.stream().distinct().count() != fields.size()) {
                throw new AqlToSqlQueryBuilderException(
                        "Duplicate fields, all the fields in the sort section should be unique.");
            }
        }
    }

    private <T extends AqlRowResult> void assertSortFieldsInResult(AqlQuery<T> aqlQuery) {
        List<AqlPhysicalFieldEnum> resultFields =
                Lists.transform(aqlQuery.getResultFields(), toAqlPhysicalFieldEnumOrNull);
        if (aqlQuery.getSort() != null && aqlQuery.getSort().getFields() != null) {
            List<AqlPhysicalFieldEnum> sortFields = aqlQuery.getSort().getFields();
            for (AqlPhysicalFieldEnum sortField : sortFields) {
                if (!resultFields.contains(sortField)) {
                    throw new AqlToSqlQueryBuilderException(
                            "Only the result fields are allowed to use in the sort section.");
                }
            }
        }
    }

    private void blockSortingForOSS(AqlQuery aqlQuery, AqlPermissionProvider permissionProvider) {
        if (permissionProvider.isOss()) {
            SortDetails sort = aqlQuery.getSort();
            if (sort != null && sort.getFields() != null && sort.getFields().size() > 0) {
                throw new AqlException("Sorting is not supported by AQL in the open source version\n");
            }
        }
    }

    private <T extends AqlRowResult> void blockSortingForTransitive(AqlQuery<T> aqlQuery) {
        SortDetails sort = aqlQuery.getSort();
        if (sort != null && sort.getFields() != null && sort.getFields().size() > 0) {
            throw new AqlException("Sorting is not supported by AQL in transitive queries");
        }
    }

    private <T extends AqlRowResult> void blockPropertyResultFilterForOSS(AqlQuery<T> aqlQuery,
            AqlPermissionProvider permissionProvider) {
        if (permissionProvider.isOss()) {
            List<AqlQueryElement> aqlElements = aqlQuery.getAqlElements();
            for (AqlQueryElement aqlElement : aqlElements) {
                if (aqlElement instanceof Criterion) {
                    SqlTable table1 = ((Criterion) aqlElement).getTable1();
                    if ((SqlTableEnum.node_props == table1.getTable() &&
                            table1.getId() < SqlTable.MINIMAL_DYNAMIC_TABLE_ID) ||
                            (SqlTableEnum.build_props == table1.getTable() &&
                                    table1.getId() < SqlTable.MINIMAL_DYNAMIC_TABLE_ID) ||
                            (SqlTableEnum.module_props == table1.getTable() &&
                                    table1.getId() < SqlTable.MINIMAL_DYNAMIC_TABLE_ID)) {
                        throw new AqlException(
                                "Filtering properties result is not supported by AQL in the open source version\n");
                    }
                }
            }
        }
    }

    private void assertActionSupportedForDomain(AqlQuery aqlQuery) {
        if (aqlQuery.getAction() == null) {
            throw new AqlException("No action was given in this query.");
        }
        if (!aqlQuery.getAction().supportsDomain(aqlQuery.getDomain())) {
            throw new AqlException("The action '" + aqlQuery.getAction().getName() + "' is unsupported for domain '" +
                    Arrays.toString(aqlQuery.getDomain().subDomains));
        }
    }
}
