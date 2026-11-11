package org.artifactory.addon.retention;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import jakarta.ws.rs.BadRequestException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ArtifactsArchiveSearchCriteriaTest {
    private static final String REPO_NAME = "someRepo";

    @Test
    void testValidationExceptions() {
        ArtifactsArchiveSearchCriteria criteria = ArtifactsArchiveSearchCriteria.builder().repositories(
                List.of(REPO_NAME, " ")).build();

        assertBadRequest(criteria, "Repositories cannot contain blank values");

        criteria = criteria.toBuilder().repositories(List.of(REPO_NAME)).build();

        criteria = criteria.toBuilder().archiveTimeFrom(-1L).build();
        assertBadRequest(criteria, "Archive time cannot be negative");
        criteria = criteria.toBuilder().archiveTimeTo(-1L).build();
        assertBadRequest(criteria, "Archive time cannot be negative");

        criteria = criteria.toBuilder().archiveTimeFrom(10L).build();
        criteria = criteria.toBuilder().archiveTimeTo(9L).build();
        assertBadRequest(criteria, "Param 'archiveTimeTo' should be greater or equal to 'archiveTimeFrom'");

        criteria = criteria.toBuilder().archiveTimeTo(10L).archiveTimeFrom(2L).artifactPath(" ").build();
        assertBadRequest(criteria, "Artifact path cannot be blank");

        ArtifactsArchiveSearchCriteria.Property property = new ArtifactsArchiveSearchCriteria.Property("key", " ");
        criteria = criteria.toBuilder().artifactPath("path").properties(List.of(property)).build();
        assertBadRequest(criteria, "Property key and value cannot be blank");

        property = new ArtifactsArchiveSearchCriteria.Property(" ", "value");
        criteria = criteria.toBuilder().properties(List.of(property)).build();
        assertBadRequest(criteria, "Property key and value cannot be blank");

        property = new ArtifactsArchiveSearchCriteria.Property("key", "*");
        criteria = criteria.toBuilder().properties(List.of(property)).build();
        assertBadRequest(criteria, "Property value cannot be a wildcard only");

    }

    @Test
    void testSuccessfulValidationFullParams() {
        ArtifactsArchiveSearchCriteria.Property property = new ArtifactsArchiveSearchCriteria.Property("key", "random");
        ArtifactsArchiveSearchCriteria criteria = ArtifactsArchiveSearchCriteria.builder()
                .repositories(List.of(REPO_NAME))
                .artifactPath("path")
                .artifactName("name*")
                .archiveTimeFrom(10L)
                .archiveTimeTo(11L)
                .properties(List.of(property))
                .sha256("920i3")
                .actualMd5("01290-3")
                .actualSha1("123-123")
                .build();
        criteria.validate();
    }

    @Test
    void testSuccessfulValidationMinRequiredParams() {
        ArtifactsArchiveSearchCriteria criteria = ArtifactsArchiveSearchCriteria.builder().build();
        criteria.validate();
    }

    @ParameterizedTest
    @MethodSource("provideValidationHasWildcard")
    void testValidationNameHasWildcard(String name, boolean expected) {
        ArtifactsArchiveSearchCriteria criteria = ArtifactsArchiveSearchCriteria.builder()
                .artifactName(name)
                .build();
        assertEquals(expected, criteria.nameContainsWildcard());
    }

    @ParameterizedTest
    @MethodSource("provideValidationHasWildcard")
    void testValidationArtifactPathHasWildcard(String name, boolean expected) {
        ArtifactsArchiveSearchCriteria criteria = ArtifactsArchiveSearchCriteria.builder()
                .artifactPath(name)
                .build();
        assertEquals(expected, criteria.artifactPathContainsWildcard());
    }

    @ParameterizedTest
    @MethodSource("provideValidationHasOnlyWildcard")
    void testValidationNameHasOnlyWildcard(String name, boolean expected) {
        ArtifactsArchiveSearchCriteria criteria = ArtifactsArchiveSearchCriteria.builder()
                .artifactName(name)
                .build();
        assertEquals(expected, criteria.nameContainsWildcardOnly());
    }

    @ParameterizedTest
    @MethodSource("provideValidationHasOnlyWildcard")
    void testValidationArtifactPathHasOnlyWildcard(String name, boolean expected) {
        ArtifactsArchiveSearchCriteria criteria = ArtifactsArchiveSearchCriteria.builder()
                .artifactPath(name)
                .build();
        assertEquals(expected, criteria.artifactPathContainsWildcardOnly());
    }

    @ParameterizedTest
    @MethodSource("provideValidationHasWildcard")
    void testValidationPropertiesHasWildcard(String name, boolean expected) {
        ArtifactsArchiveSearchCriteria.Property property1 = new ArtifactsArchiveSearchCriteria.Property("key", name);
        ArtifactsArchiveSearchCriteria.Property property2 = new ArtifactsArchiveSearchCriteria.Property("key", name);

        assertEquals(expected, property1.propertyValueContainsWildcard());
        assertEquals(expected, property2.propertyValueContainsWildcard());
    }

    @Test
    void testToBuilder() {
        ArtifactsArchiveSearchCriteria criteria = ArtifactsArchiveSearchCriteria.builder()
                .repositories(List.of(REPO_NAME))
                .artifactPath("path")
                .artifactName("name")
                .archiveTimeFrom(10L)
                .archiveTimeTo(20L)
                .sha256("sha256")
                .actualSha1("sha1")
                .actualMd5("md5")
                .build();

        // Use toBuilder to create a new builder and modify some properties
        ArtifactsArchiveSearchCriteria newCriteria = criteria.toBuilder()
                .artifactPath("newPath")
                .archiveTimeFrom(15L)
                .build();

        // Verify that the new object has the updated properties
        assertEquals("newPath", newCriteria.artifactPath());
        assertEquals(15L, newCriteria.archiveTimeFrom());

        // Verify that the original object remains unchanged
        assertEquals("path", criteria.artifactPath());
        assertEquals(10L, criteria.archiveTimeFrom());
    }

    private void assertBadRequest(ArtifactsArchiveSearchCriteria criteria, String message) {
        BadRequestException thrown = assertThrows(BadRequestException.class, criteria::validate);
        assertEquals(message, thrown.getMessage());
    }

    private static Stream<Arguments> provideValidationHasWildcard() {
        return Stream.of(
                Arguments.of(null, false),
                Arguments.of("", false),
                Arguments.of("  ", false),
                Arguments.of("not blank", false),
                Arguments.of("*name", true),
                Arguments.of("name*", true),
                Arguments.of("*name*", true),
                Arguments.of("**", true)
        );
    }

    private static Stream<Arguments> provideValidationHasOnlyWildcard() {
        return Stream.of(
                Arguments.of(null, false),
                Arguments.of("", false),
                Arguments.of("  ", false),
                Arguments.of("not blank", false),
                Arguments.of("*name", false),
                Arguments.of("name*", false),
                Arguments.of("*name*", false),
                Arguments.of("**", false),
                Arguments.of("*", true)
        );
    }
}
