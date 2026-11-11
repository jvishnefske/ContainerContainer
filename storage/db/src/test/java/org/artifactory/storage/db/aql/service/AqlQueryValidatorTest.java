package org.artifactory.storage.db.aql.service;

import com.google.common.collect.Lists;
import org.artifactory.aql.action.AqlAction;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlPermissionProvider;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.parser.AqlParser;
import org.artifactory.storage.db.aql.parser.ParserElementResultContainer;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.ParserToAqlAdapter;
import org.artifactory.storage.db.aql.sql.builder.query.aql.TransitiveMetadata;
import org.artifactory.storage.db.aql.sql.builder.query.sql.AqlToSqlQueryBuilderException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.artifactory.aql.model.AqlPhysicalFieldEnum.buildName;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.buildNumber;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.buildRepo;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemName;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemPath;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemRepo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomer Slutsky
 */
class AqlQueryValidatorTest extends ArtifactoryHomeBoundJUnitTest {
    private final AqlParser parser = new AqlParser();
    private final AqlQueryValidator validator = new AqlQueryValidator();
    private final ParserToAqlAdapter aqlAdapter = new ParserToAqlAdapter();

    @Test
    void validateTransitiveQuery() {
        assertValid("items.find({\"repo\": \"works\"}).transitive()");
        assertValid("items.find({\"repo\": \"works\"}).include(\"repo\").transitive()");
        assertValid("items.find({\"repo\": \"works\"},{\"name\":\"working\"}).include(\"repo\").transitive()");
        assertValid("items.find({\"repo\": \"works\"}).include(\"repo\", \"property.*\").transitive()");
        assertValid("items.find({\"repo\": \"works\"}).include(\"repo\", \"property.key\").transitive()");
        assertValid("items.find({\"repo\": \"works\"}).include(\"repo\", \"property.value\").transitive()");
        assertValid("items.find({\"repo\": \"works\"}).include(\"repo\", \"@bla\").transitive()");
        assertInvalid("items.find({\"repo\": \"works\"}).include(\"repo\", \"@bla\").transitive().offset(10)");
        assertInvalid("items.find({\"repo\": \"works\"}).include(\"repo\", \"@bla\").transitive().sort({\"$asc" +
                "\" : [\"repo\"]})");
        assertInvalid("artifacts.find().transitive()");
        assertInvalid("items.find().include(\"repo\", \"artifact.module.build.name\").transitive()");
        assertInvalid("items.find().include(\"repo\", \"artifact.module.build.@bla\").transitive()");
        assertInvalid(
                "items.find({\"repo\": \"works\"}).include(\"repo\", \"artifact.module.build.name\").transitive()");
        assertInvalid(
                "items.find({\"name\": \"worksnot\"}).transitive()");
        assertInvalid(
                "items.find({\"name\": \"worksnot\"}).include(\"repo\").transitive()");
        assertInvalid(
                "items.find({\"name\": \"worksnot\"}).include(\"repo\", \"@bla\").transitive()");
        assertInvalid(
                "items.find({\"$or\":[{\"repo\": \"worksnot\"},{\"repo\": \"worksnot2\"}]}).transitive()");
        assertInvalid("items.find({\"repo\": \"worksnot\"},{\"repo\": \"worksnot2\"}).transitive()");
        assertInvalid(
                "items.find({\"$or\":[{\"repo\": \"worksnot\"},{\"repo\": \"worksnot2\"}]}).include(\"repo\", " +
                        "\"@bla\").transitive()");
    }

    private void assertValid(String query) {
        AqlPermissionProvider permissionMock = mock(AqlPermissionProvider.class);
        when(permissionMock.isOss()).thenReturn(false);
        when(permissionMock.isAdmin()).thenReturn(true);
        ParserElementResultContainer container = parser.parse(query);
        AqlQuery<AqlRowResult> aqlQuery = aqlAdapter.toAqlModel(container);
        validator.validate(aqlQuery, permissionMock);
    }

    private void assertInvalid(String query) {
        try {
            AqlPermissionProvider permissionMock = mock(AqlPermissionProvider.class);
            when(permissionMock.isOss()).thenReturn(false);
            when(permissionMock.isAdmin()).thenReturn(true);
            ParserElementResultContainer container = parser.parse(query);
            AqlQuery<AqlRowResult> aqlQuery = aqlAdapter.toAqlModel(container);
            validator.validate(aqlQuery, permissionMock);
            fail("Expected query validation to fail");
        } catch (Exception e) {
            // Expected exception
        }
    }

    @Test
    void assertMinimalResultFields_shouldSkipValidation_whenUserIsAdmin() {
        // Arrange
        AqlPermissionProvider permissionProvider = mock(AqlPermissionProvider.class);
        when(permissionProvider.isAdmin()).thenReturn(true);
        AqlQuery<AqlRowResult> aqlQuery = createMockAqlQuery(AqlDomainEnum.items, Collections.emptyList());

        // Act & Assert
        assertDoesNotThrow(() -> validator.validate(aqlQuery, permissionProvider));
    }

    @Test
    void assertMinimalResultFields_shouldPass_whenItemsDomainHasAllRequiredFields() {
        // Arrange
        AqlPermissionProvider permissionProvider = mock(AqlPermissionProvider.class);
        when(permissionProvider.isAdmin()).thenReturn(false);
        AqlQuery<AqlRowResult> aqlQuery = createMockAqlQuery(
                AqlDomainEnum.items,
                createDomainSensitiveFields(itemRepo, itemPath, itemName)
        );

        // Act & Assert
        assertDoesNotThrow(() -> validator.validate(aqlQuery, permissionProvider));
    }

    @Test
    void assertMinimalResultFields_shouldThrowException_whenItemsDomainMissingRequiredField() {
        // Arrange
        AqlPermissionProvider permissionProvider = mock(AqlPermissionProvider.class);
        when(permissionProvider.isAdmin()).thenReturn(false);
        AqlQuery<AqlRowResult> aqlQuery = createMockAqlQuery(
                AqlDomainEnum.items,
                createDomainSensitiveFields(itemPath, itemName)
        );

        // Act & Assert
        AqlToSqlQueryBuilderException exception = assertThrows(
                AqlToSqlQueryBuilderException.class,
                () -> validator.validate(aqlQuery, permissionProvider)
        );
        assertEquals(
                "For permissions reasons AQL items domain demands the following fields: repo, path and name.",
                exception.getMessage()
        );
    }

    @Test
    void assertMinimalResultFields_shouldPass_whenBuildsDomainHasAllRequiredFields() {
        // Arrange
        AqlPermissionProvider permissionProvider = mock(AqlPermissionProvider.class);
        when(permissionProvider.isAdmin()).thenReturn(false);
        AqlQuery<AqlRowResult> aqlQuery = createMockAqlQuery(
                AqlDomainEnum.builds,
                createDomainSensitiveFields(buildName, buildNumber, buildRepo)
        );

        // Act & Assert
        assertDoesNotThrow(() -> validator.validate(aqlQuery, permissionProvider));
    }

    @Test
    void assertMinimalResultFields_shouldThrowException_whenBuildsDomainMissingRequiredField() {
        // Arrange
        AqlPermissionProvider permissionProvider = mock(AqlPermissionProvider.class);
        when(permissionProvider.isAdmin()).thenReturn(false);
        AqlQuery<AqlRowResult> aqlQuery = createMockAqlQuery(
                AqlDomainEnum.builds,
                createDomainSensitiveFields(buildNumber, buildRepo)
        );

        // Act & Assert
        AqlToSqlQueryBuilderException exception = assertThrows(
                AqlToSqlQueryBuilderException.class,
                () -> validator.validate(aqlQuery, permissionProvider)
        );
        assertEquals(
                "For permissions reasons AQL builds domain demands the following fields: name, number and repo.",
                exception.getMessage()
        );
    }

    @Test
    void assertMinimalResultFields_shouldPass_whenOtherDomain() {
        // Arrange - properties domain should not be validated
        AqlPermissionProvider permissionProvider = mock(AqlPermissionProvider.class);
        when(permissionProvider.isAdmin()).thenReturn(false);
        AqlQuery<AqlRowResult> aqlQuery = createMockAqlQuery(
                AqlDomainEnum.properties,
                Collections.emptyList()
        );

        // Act & Assert
        assertDoesNotThrow(() -> validator.validate(aqlQuery, permissionProvider));
    }

    private AqlQuery<AqlRowResult> createMockAqlQuery(AqlDomainEnum domain, List<DomainSensitiveField> resultFields) {
        AqlQuery<AqlRowResult> aqlQuery = mock(AqlQuery.class);
        when(aqlQuery.getDomain()).thenReturn(domain);
        when(aqlQuery.getResultFields()).thenReturn(resultFields);

        TransitiveMetadata transitiveMetadata = mock(TransitiveMetadata.class);
        when(transitiveMetadata.isTransitive()).thenReturn(false);
        when(aqlQuery.getTransitiveMetadata()).thenReturn(transitiveMetadata);
        when(aqlQuery.getSort()).thenReturn(null);
        when(aqlQuery.getAqlElements()).thenReturn(Collections.emptyList());
        when(aqlQuery.getOffset()).thenReturn(0L);

        AqlAction action = mock(AqlAction.class);
        when(action.supportsDomain(any())).thenReturn(true);
        when(aqlQuery.getAction()).thenReturn(action);

        return aqlQuery;
    }

    private List<DomainSensitiveField> createDomainSensitiveFields(AqlPhysicalFieldEnum... fields) {
        List<DomainSensitiveField> result = Lists.newArrayList();
        for (AqlPhysicalFieldEnum field : fields) {
            DomainSensitiveField dsf = mock(DomainSensitiveField.class);
            when(dsf.getField()).thenReturn(field);
            result.add(dsf);
        }
        return result;
    }
}
